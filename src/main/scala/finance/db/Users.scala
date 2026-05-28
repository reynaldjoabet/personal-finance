package finance.db

import finance.domain.*

import cats.effect.*
import cats.syntax.all.*
import skunk.*
import skunk.implicits.*

trait Users[F[_]] {
  def create(email: Email, name: DisplayName): F[User]
  def find(id: UserId): F[Option[User]]
  def findByEmail(email: Email): F[Option[User]]
}

object Users {

  import Codecs.{email as emailC, displayName as nameC, user as userC, userId as userIdC}

  def make[F[_]: Concurrent](pool: Resource[F, Session[F]]): Users[F] =
    new Users[F] {

      def create(emailV: Email, nameV: DisplayName): F[User] =
        pool.use(_.prepare(Q.insert).flatMap(_.unique(emailV, nameV)))

      def find(id: UserId): F[Option[User]] =
        pool.use(_.prepare(Q.byId).flatMap(_.option(id)))

      def findByEmail(emailV: Email): F[Option[User]] =
        pool.use(_.prepare(Q.byEmail).flatMap(_.option(emailV)))
    }

  private object Q {

    val insert: Query[(Email, DisplayName), User] =
      sql"""
        INSERT INTO users (email, display_name)
        VALUES ($emailC, $nameC)
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
  }
}
