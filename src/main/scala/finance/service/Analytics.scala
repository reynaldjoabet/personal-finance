package finance.service

import java.time.{LocalDate, YearMonth}

import cats.effect.*
import cats.syntax.all.*

import finance.db.Transactions
import finance.domain.*

trait Analytics[F[_]] {

  def monthly(user: UserId, month: YearMonth): F[MonthlySummary]
  def lastNMonths(user: UserId, n: Int): F[List[MonthlySummary]]

}

object Analytics {

  def make[F[_]: Concurrent](txs: Transactions[F]): Analytics[F] =
    new Analytics[F] {

      def monthly(user: UserId, month: YearMonth): F[MonthlySummary] = {
        val from = month.atDay(1)
        val to   = month.atEndOfMonth
        txs.betweenForUser(user, from, to).map(rollup(month, _))
      }

      def lastNMonths(user: UserId, n: Int): F[List[MonthlySummary]] = {
        val today  = LocalDate.now()
        val months = (0 until n).toList.map(i => YearMonth.from(today.minusMonths(i.toLong)))
        months.traverse(monthly(user, _))
      }
    }

  private def rollup(month: YearMonth, ts: List[Transaction]): MonthlySummary = {
    val inflow  = ts.collect { case t if t.amount.value > 0L => t.amount.value }.sum
    val outflow = ts.collect { case t if t.amount.value < 0L => -t.amount.value }.sum

    val byCat: List[CategoryTotal] =
      ts.groupBy(_.category)
        .collect { case (Some(cat), rows) =>
          CategoryTotal(cat, rows.map(_.amount.value).sum, rows.size)
        }
        .toList
        .sortBy(-_.totalMinor.abs)

    MonthlySummary(
      month = month,
      inflowMinor = inflow,
      outflowMinor = outflow,
      netMinor = inflow - outflow,
      byCategory = byCat
    )
  }

}
