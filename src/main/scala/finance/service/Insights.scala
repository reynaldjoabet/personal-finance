package finance.service

import cats.effect.*
import cats.syntax.all.*

import finance.domain.*

/**
  * "ML engine" — rule-driven heuristics over the last 6 months of summaries. Swap with a real model
  * when one exists; the signature is stable.
  */
trait Insights[F[_]] {
  def forUser(user: UserId): F[List[Insight]]
}

object Insights {

  def make[F[_]: Concurrent](analytics: Analytics[F]): Insights[F] =
    new Insights[F] {

      def forUser(user: UserId): F[List[Insight]] =
        analytics.lastNMonths(user, 6).map(detect)
    }

  private val MonthsWindow = 6

  /**
    * Heuristics over the rolling window. Each returns 0..1 insight.
    */
  private def detect(window: List[MonthlySummary]): List[Insight] =
    if (window.size < 3) Nil
    else
      List(
        outflowTrend(window),
        categoryCreep(window),
        savingsOpportunity(window)
      ).flatten

  /**
    * Outflow rising >15% relative to the trailing average.
    */
  private def outflowTrend(w: List[MonthlySummary]): Option[Insight] = {
    val (recent :: rest) = w: @unchecked
    val avg              = rest.map(_.outflowMinor).sum.toDouble / rest.size.max(1)
    if (avg > 0 && recent.outflowMinor > avg * 1.15) {
      val delta = recent.outflowMinor - avg.toLong
      Some(
        Insight(
          title = DisplayName.applyUnsafe("Spending is climbing"),
          body = f"Your outflow in ${recent.month} was ${recent.outflowMinor / 100.0}%.2f " +
            f"vs a ${MonthsWindow - 1}-month average of ${avg / 100}%.2f.",
          estimatedSavingMinor = Some(delta)
        )
      )
    } else None
  }

  /**
    * A category that grew >25% MoM for two months running.
    */
  private def categoryCreep(w: List[MonthlySummary]): Option[Insight] = {
    val series = w.take(3).reverse // oldest -> newest
    if (series.size < 3) None
    else {
      val cats = series.flatMap(_.byCategory.map(_.category)).distinct
      cats.collectFirst(Function.unlift { c =>
        val totals = series.map(_.byCategory.find(_.category == c).fold(0L)(_.totalMinor.abs))
        if (totals.forall(_ > 0) && totals(1) > totals(0) * 1.25 && totals(2) > totals(1) * 1.25)
          Some(
            Insight(
              title = DisplayName.applyUnsafe(s"'${c.value}' is creeping up"),
              body = s"Your spending on ${c.value} has grown in each of the last three months.",
              estimatedSavingMinor = Some(totals(2) - totals(0))
            )
          )
        else None
      })
    }
  }

  /**
    * Recent net was strongly positive — suggest moving idle cash to savings.
    */
  private def savingsOpportunity(w: List[MonthlySummary]): Option[Insight] = {
    val recent = w.headOption
    recent.collect {
      case s if s.netMinor > 50_000L => // >£500 surplus
        Insight(
          title = DisplayName.applyUnsafe("You can sweep cash to savings"),
          body = f"You ended ${s.month} with a surplus of ${s.netMinor / 100.0}%.2f. " +
            "Tap to move it to your savings account.",
          estimatedSavingMinor = Some(s.netMinor)
        )
    }
  }

}
