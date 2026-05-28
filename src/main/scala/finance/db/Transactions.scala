package finance.db

import finance.domain.*

import cats.effect.*
import cats.syntax.all.*
import skunk.{Transaction as _, *}
import skunk.implicits.*
import skunk.codec.all.*

import java.time.LocalDate

trait Transactions[F[_]] {
  def insertAll(txs: List[Transaction]): F[Unit]
  def between(account: AccountId, from: LocalDate, to: LocalDate): F[List[Transaction]]
  def betweenForUser(user: UserId, from: LocalDate, to: LocalDate): F[List[Transaction]]
}

object Transactions {

  import Codecs.{
    transaction as txC,
    accountId as accountIdC,
    userId as userIdC,
    amountMinor,
    currency,
    merchant,
    category,
    transactionId as txIdC
  }

  def make[F[_]: Concurrent](pool: Resource[F, Session[F]]): Transactions[F] =
    new Transactions[F] {

      def insertAll(txs: List[Transaction]): F[Unit] =
        if (txs.isEmpty) Concurrent[F].unit
        else
          pool.use(s => s.prepare(Q.insertOne).flatMap(pc => txs.traverse_(pc.execute)))

      def between(account: AccountId, from: LocalDate, to: LocalDate): F[List[Transaction]] =
        pool.use(_.prepare(Q.betweenForAccount).flatMap(_.stream((account, from, to), 256).compile.toList))

      def betweenForUser(user: UserId, from: LocalDate, to: LocalDate): F[List[Transaction]] =
        pool.use(_.prepare(Q.betweenForUser).flatMap(_.stream((user, from, to), 256).compile.toList))
    }

  private object Q {

    val insertOne: Command[Transaction] =
      sql"""
        INSERT INTO transactions (id, account_id, occurred_on, amount_minor, currency, merchant, category)
        VALUES ($txIdC, $accountIdC, $date, $amountMinor, $currency, $merchant, ${category.opt})
        ON CONFLICT (id) DO NOTHING
      """.command
        .contramap[Transaction](t => (t.id, t.accountId, t.occurredOn, t.amount, t.currency, t.merchant, t.category))

    val betweenForAccount: Query[(AccountId, LocalDate, LocalDate), Transaction] =
      sql"""
        SELECT id, account_id, occurred_on, amount_minor, currency, merchant, category
        FROM transactions
        WHERE account_id = $accountIdC AND occurred_on BETWEEN $date AND $date
        ORDER BY occurred_on DESC
      """.query(txC)

    val betweenForUser: Query[(UserId, LocalDate, LocalDate), Transaction] =
      sql"""
        SELECT t.id, t.account_id, t.occurred_on, t.amount_minor, t.currency, t.merchant, t.category
        FROM transactions t
        JOIN accounts a ON a.id = t.account_id
        WHERE a.user_id = $userIdC AND t.occurred_on BETWEEN $date AND $date
        ORDER BY t.occurred_on DESC
      """.query(txC)
  }
}
