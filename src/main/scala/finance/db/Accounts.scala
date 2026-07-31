package finance.db

import cats.effect.*
import cats.syntax.all.*

import finance.domain.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

trait Accounts[F[_]] {

  def listFor(user: UserId): F[List[Account]]
  def upsert(account: Account): F[Unit]

}

object Accounts {

  import Codecs.{
    account as accountC,
    accountId as accountIdC,
    accountKind,
    balanceMinor,
    currency,
    userId as userIdC
  }

  def make[F[_]: Concurrent](pool: Resource[F, Session[F]]): Accounts[F] =
    new Accounts[F] {

      def listFor(user: UserId): F[List[Account]] =
        pool.use(_.prepare(Q.listForUser).flatMap(_.stream(user, 64).compile.toList))

      def upsert(a: Account): F[Unit] =
        pool.use(_.prepare(Q.upsert).flatMap(_.execute(a))).void
    }

  private object Q {

    val listForUser: Query[UserId, Account] =
      sql"""
        SELECT id, user_id, provider, external_id, kind, currency, balance_minor, updated_at
        FROM accounts WHERE user_id = $userIdC
        ORDER BY updated_at DESC
      """.query(accountC)

    val upsert: Command[Account] =
      sql"""
        INSERT INTO accounts (id, user_id, provider, external_id, kind, currency, balance_minor, updated_at)
        VALUES ($accountIdC, $userIdC, ${varchar(64)}, ${varchar(
          128
        )}, $accountKind, $currency, $balanceMinor, $timestamptz)
        ON CONFLICT (provider, external_id) DO UPDATE
          SET balance_minor = EXCLUDED.balance_minor,
              updated_at    = EXCLUDED.updated_at
      """.command
        .contramap[Account](a =>
          (
            a.id,
            a.userId,
            a.provider,
            a.externalId,
            a.kind,
            a.currency,
            a.balance,
            a.updatedAt.atOffset(java.time.ZoneOffset.UTC)
          )
        )

  }

}
