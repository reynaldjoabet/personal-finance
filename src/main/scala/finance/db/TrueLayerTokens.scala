package finance.db

import java.time.Instant

import cats.effect.*
import cats.syntax.all.*

import finance.domain.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

/**
  * Per-user OAuth tokens for TrueLayer. We store the refresh token alongside the access token so
  * the sync job can mint a fresh access token without dragging the user back through the consent
  * screen.
  */
final case class TlTokens(
    accessToken: String,
    refreshToken: String,
    expiresAt: Instant,
    scope: Option[String]
)

trait TrueLayerTokens[F[_]] {

  def upsert(userId: UserId, tokens: TlTokens): F[Unit]
  def find(userId: UserId): F[Option[TlTokens]]
  def delete(userId: UserId): F[Unit]

}

object TrueLayerTokens {

  import Codecs.{instant as instantC, userId as userIdC}

  def make[F[_]: Concurrent](pool: Resource[F, Session[F]]): TrueLayerTokens[F] =
    new TrueLayerTokens[F] {

      def upsert(uid: UserId, t: TlTokens): F[Unit] =
        pool.use(_.prepare(Q.upsert).flatMap(_.execute((uid, t)))).void

      def find(uid: UserId): F[Option[TlTokens]] =
        pool.use(_.prepare(Q.byUser).flatMap(_.option(uid)))

      def delete(uid: UserId): F[Unit] =
        pool.use(_.prepare(Q.delete).flatMap(_.execute(uid))).void
    }

  private val tokensC: Codec[TlTokens] =
    (text *: text *: instantC *: text.opt).imap { case (a, r, e, s) =>
      TlTokens(a, r, e, s)
    }(t => (t.accessToken, t.refreshToken, t.expiresAt, t.scope))

  private object Q {

    val upsert: Command[(UserId, TlTokens)] =
      sql"""
        INSERT INTO truelayer_tokens (user_id, access_token, refresh_token, expires_at, scope, updated_at)
        VALUES ($userIdC, $text, $text, $instantC, ${text.opt}, $instantC)
        ON CONFLICT (user_id) DO UPDATE
          SET access_token  = EXCLUDED.access_token,
              refresh_token = EXCLUDED.refresh_token,
              expires_at    = EXCLUDED.expires_at,
              scope         = EXCLUDED.scope,
              updated_at    = EXCLUDED.updated_at
      """.command.contramap[(UserId, TlTokens)] { case (uid, t) =>
        (uid, t.accessToken, t.refreshToken, t.expiresAt, t.scope, Instant.now())
      }

    val byUser: Query[UserId, TlTokens] =
      sql"""
        SELECT access_token, refresh_token, expires_at, scope
        FROM truelayer_tokens WHERE user_id = $userIdC
      """.query(tokensC)

    val delete: Command[UserId] =
      sql"DELETE FROM truelayer_tokens WHERE user_id = $userIdC".command

  }

}
