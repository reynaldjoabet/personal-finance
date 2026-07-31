package finance.db

import cats.effect.*
import cats.syntax.all.*

import finance.domain.*
import skunk.*
import skunk.implicits.*

trait Users[F[_]] {

  /**
    * Create a new user with credentials. Caller is responsible for hashing the password.
    */
  def register(email: Email, name: DisplayName, hash: PasswordHash): F[User]

  def find(id: UserId): F[Option[User]]
  def findByEmail(email: Email): F[Option[User]]

  /**
    * Look up the stored bcrypt hash for password verification. Returns the user + hash together so
    * the caller can issue a token without a second round trip.
    */
  def findCredentials(email: Email): F[Option[(User, PasswordHash)]]

}

object Users {

  import Codecs.{
    displayName as nameC,
    email as emailC,
    passwordHash as hashC,
    user as userC,
    userId as userIdC
  }

  def make[F[_]: Concurrent](pool: Resource[F, Session[F]]): Users[F] =
    new Users[F] {

      def register(emailV: Email, nameV: DisplayName, hashV: PasswordHash): F[User] =
        pool.use(_.prepare(Q.insert).flatMap(_.unique(emailV, nameV, hashV)))

      def find(id: UserId): F[Option[User]] =
        pool.use(_.prepare(Q.byId).flatMap(_.option(id)))

      def findByEmail(emailV: Email): F[Option[User]] =
        pool.use(_.prepare(Q.byEmail).flatMap(_.option(emailV)))

      def findCredentials(emailV: Email): F[Option[(User, PasswordHash)]] =
        pool.use(_.prepare(Q.credsByEmail).flatMap(_.option(emailV)))
    }

  private object Q {

    val insert: Query[(Email, DisplayName, PasswordHash), User] =
      sql"""
        INSERT INTO users (email, display_name, password_hash)
        VALUES ($emailC, $nameC, $hashC)
        RETURNING id, email, display_name, created_at
      """.query(userC)

    val byId: Query[UserId, User] =
      sql"""
        SELECT id, email, display_name, created_at
        FROM users WHERE id = $userIdC
      """.query(userC)

    val byEmail: Query[Email, User] =
      sql"""
        SELECT id, email, display_name, created_at
        FROM users WHERE email = $emailC
      """.query(userC)

    val credsByEmail: Query[Email, (User, PasswordHash)] =
      sql"""
        SELECT id, email, display_name, created_at, password_hash
        FROM users WHERE email = $emailC
      """.query(userC *: hashC)

  }

}
