package finance.service

import finance.db.{Accounts, TlTokens, Transactions, TrueLayerTokens}
import finance.domain.*
import finance.truelayer.TrueLayer

import cats.effect.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import java.time.{Instant, LocalDate}

/** On-demand sync from TrueLayer into our DB. Pulls the user's accounts, then transactions for each account over the
  * last `windowDays` days, refreshes the access token if it's expired or within the safety window, and persists via the
  * `Accounts` / `Transactions` repos.
  *
  * Malformed rows from TrueLayer are skipped with a warning rather than failing the whole sync — partial progress beats
  * none.
  */
trait TrueLayerSync[F[_]] {
  def syncUser(userId: UserId, windowDays: Int = 90): F[TrueLayerSync.Result]
}

object TrueLayerSync {

  final case class Result(accountsUpserted: Int, transactionsInserted: Int, skipped: Int)

  sealed trait Error extends Product with Serializable
  object Error {
    case object NotConnected extends Error
    final case class Upstream(message: String) extends Error
  }

  /** Refresh ~60s before actual expiry so a slow round-trip can't expire mid-call. */
  private val refreshSkewSeconds: Long = 60L

  def make[F[_]: Async: Logger](
      tl: TrueLayer[F],
      tokens: TrueLayerTokens[F],
      accounts: Accounts[F],
      transactions: Transactions[F]
  ): TrueLayerSync[F] =
    new TrueLayerSync[F] {

      def syncUser(userId: UserId, windowDays: Int = 90): F[Result] =
        tokens.find(userId).flatMap {
          case None    => Async[F].raiseError(new RuntimeException("user has not connected TrueLayer"))
          case Some(t) =>
            freshAccess(userId, t).flatMap { active =>
              val accessTok = TrueLayer.AccessToken(active.accessToken)
              tl.listAccounts(accessTok).flatMap { rawAccounts =>
                val to = LocalDate.now()
                val from = to.minusDays(windowDays.toLong)

                rawAccounts.foldLeftM(Result(0, 0, 0)) { (acc, raw) =>
                  TrueLayer.toDomainAccount(userId, raw) match {
                    case Left(err) =>
                      Logger[F]
                        .warn(s"skipping account ${raw.account_id}: $err")
                        .as(acc.copy(skipped = acc.skipped + 1))
                    case Right(account) =>
                      for {
                        _ <- accounts.upsert(account)
                        rawTxs <- tl.listTransactions(accessTok, account.externalId, from, to)
                        // partitionMap: Left -> first slot, Right -> second. We want the failures (with their raw row
                        // for the log line) on the left and the successfully-promoted Transactions on the right.
                        partitioned = rawTxs.partitionMap { (r: TrueLayer.RawTransaction) =>
                          TrueLayer.toDomainTransaction(account.id, account.currency, r).left.map(err => (r, err))
                        }
                        (bad, good) = partitioned
                        _ <- bad.traverse_ { case (r, err) =>
                          Logger[F].warn(s"skipping tx ${r.transaction_id}: $err")
                        }
                        _ <- transactions.insertAll(good)
                      } yield acc.copy(
                        accountsUpserted = acc.accountsUpserted + 1,
                        transactionsInserted = acc.transactionsInserted + good.size,
                        skipped = acc.skipped + bad.size
                      )
                  }
                }
              }
            }
        }

      private def freshAccess(userId: UserId, current: TlTokens): F[TlTokens] = {
        val now = Instant.now()
        if (current.expiresAt.isAfter(now.plusSeconds(refreshSkewSeconds))) current.pure[F]
        else
          tl.refresh(current.refreshToken).flatTap(tokens.upsert(userId, _))
      }
    }
}
