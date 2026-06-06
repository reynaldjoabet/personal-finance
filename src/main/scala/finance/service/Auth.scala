package finance.service

import finance.config.AuthConfig
import finance.db.Users
import finance.domain.*

import cats.effect.*
import cats.syntax.all.*
import com.nimbusds.jose.{JOSEException, JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.{RSASSASigner, RSASSAVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import com.password4j.Password

import java.security.{KeyFactory, PrivateKey, PublicKey}
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.time.Instant
import java.util.{Base64, Date, UUID}

/** Authentication: bcrypt-based password verification plus RS256 JWT issuance/verification.
  *
  * `register` and `login` return a `Token` containing a serialized JWT whose subject is the user id. `verify` is what
  * the http4s middleware calls on every request — it parses, checks the signature, and enforces issuer / audience /
  * expiry before handing back the `UserId`.
  */
trait Auth[F[_]] {
  def register(email: Email, name: DisplayName, password: PlainPassword): F[Either[Auth.Error, (User, Auth.Token)]]
  def login(email: Email, password: PlainPassword): F[Either[Auth.Error, (User, Auth.Token)]]
  def verify(token: String): F[Either[Auth.Error, UserId]]
}

object Auth {

  final case class Token(value: String, expiresAt: Instant)

  sealed trait Error extends Product with Serializable
  object Error {
    case object EmailTaken extends Error
    case object InvalidCredentials extends Error
    case object InvalidToken extends Error
    final case class Internal(message: String) extends Error
  }

  /** Strip the PEM armor and decode the body as base64. Works for both PKCS#8 private keys and X.509 public keys. */
  private def decodePem(pem: String): Array[Byte] = {
    val cleaned = pem.linesIterator
      .map(_.trim)
      .filterNot(l => l.isEmpty || l.startsWith("-----"))
      .mkString
    Base64.getDecoder.decode(cleaned)
  }

  private def loadPrivateKey(pem: String): RSAPrivateKey = {
    val spec = new PKCS8EncodedKeySpec(decodePem(pem))
    KeyFactory.getInstance("RSA").generatePrivate(spec).asInstanceOf[RSAPrivateKey]
  }

  private def loadPublicKey(pem: String): RSAPublicKey = {
    val spec = new X509EncodedKeySpec(decodePem(pem))
    KeyFactory.getInstance("RSA").generatePublic(spec).asInstanceOf[RSAPublicKey]
  }

  def make[F[_]: Sync](users: Users[F], cfg: AuthConfig): F[Auth[F]] = Sync[F].delay {

    val privateKey: PrivateKey = loadPrivateKey(cfg.privateKeyPem)
    val publicKey: PublicKey = loadPublicKey(cfg.publicKeyPem)
    val signer = new RSASSASigner(privateKey)
    val verifier = new RSASSAVerifier(publicKey.asInstanceOf[RSAPublicKey])

    new Auth[F] {

      private def issue(userId: UserId): F[Token] = Sync[F].delay {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(cfg.expirySeconds)
        val claims = new JWTClaimsSet.Builder()
          .subject(userId.value.toString)
          .issuer(cfg.issuer)
          .audience(cfg.audience)
          .issueTime(Date.from(now))
          .expirationTime(Date.from(expiresAt))
          .jwtID(UUID.randomUUID().toString)
          .build()
        val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims)
        jwt.sign(signer)
        Token(jwt.serialize(), expiresAt)
      }

      private def hash(plain: PlainPassword): F[PasswordHash] = Sync[F].delay {
        val s = Password.hash(plain.value).withBcrypt().getResult
        PasswordHash.assume(s)
      }

      private def check(plain: PlainPassword, stored: PasswordHash): F[Boolean] = Sync[F].delay {
        Password.check(plain.value, stored.value).withBcrypt()
      }

      def register(email: Email, name: DisplayName, password: PlainPassword): F[Either[Error, (User, Token)]] =
        users.findByEmail(email).flatMap {
          case Some(_) => Sync[F].pure(Left(Error.EmailTaken))
          case None    =>
            hash(password).flatMap { h =>
              users.register(email, name, h).flatMap { user =>
                issue(user.id).map(tok => Right((user, tok)))
              }
            }
        }

      def login(email: Email, password: PlainPassword): F[Either[Error, (User, Token)]] =
        users.findCredentials(email).flatMap {
          case None                     => Sync[F].pure(Left(Error.InvalidCredentials))
          case Some((user, storedHash)) =>
            check(password, storedHash).flatMap {
              case false => Sync[F].pure(Left(Error.InvalidCredentials))
              case true  => issue(user.id).map(tok => Right((user, tok)))
            }
        }

      def verify(token: String): F[Either[Error, UserId]] = Sync[F].delay {
        try {
          val jwt = SignedJWT.parse(token)
          if (!jwt.verify(verifier)) Left(Error.InvalidToken)
          else {
            val claims = jwt.getJWTClaimsSet
            val now = new Date()
            val okIssuer = claims.getIssuer == cfg.issuer
            val okAudience = Option(claims.getAudience).exists(_.contains(cfg.audience))
            val notExpired = Option(claims.getExpirationTime).exists(_.after(now))
            if (!okIssuer || !okAudience || !notExpired) Left(Error.InvalidToken)
            else
              scala.util
                .Try(UUID.fromString(claims.getSubject))
                .toEither
                .left
                .map(_ => Error.InvalidToken)
                .map(UserId.assume)
          }
        } catch {
          case _: java.text.ParseException => Left(Error.InvalidToken)
          case _: JOSEException            => Left(Error.InvalidToken)
        }
      }
    }
  }
}
