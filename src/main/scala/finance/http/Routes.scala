package finance.http

import finance.domain.*
import finance.db.*
import finance.service.*

import cats.effect.*
import cats.syntax.all.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.Http4sDsl

import java.time.YearMonth
import java.util.UUID

final class Routes[F[_]: Concurrent](
    users: Users[F],
    accounts: Accounts[F],
    analytics: Analytics[F],
    insights: Insights[F]
) extends Http4sDsl[F] {

  import Json.given

  private object MonthVar {
    def unapply(s: String): Option[YearMonth] =
      scala.util.Try(YearMonth.parse(s)).toOption
  }

  private object UserIdVar {
    def unapply(s: String): Option[UserId] =
      scala.util.Try(UUID.fromString(s)).toOption.map(UserId.assume)
  }

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "health" =>
      Ok(Map("status" -> "ok").asJson)

    case req @ POST -> Root / "users" =>
      req.as[Json.CreateUser].flatMap { c =>
        users.create(c.email, c.displayName).flatMap(Created(_))
      }

    case GET -> Root / "users" / UserIdVar(id) =>
      users.find(id).flatMap {
        case Some(u) => Ok(u)
        case None    => NotFound()
      }

    case GET -> Root / "users" / UserIdVar(id) / "accounts" =>
      accounts.listFor(id).flatMap(Ok(_))

    case GET -> Root / "users" / UserIdVar(id) / "summary" / MonthVar(m) =>
      analytics.monthly(id, m).flatMap(Ok(_))

    case GET -> Root / "users" / UserIdVar(id) / "summary" =>
      analytics.lastNMonths(id, 6).flatMap(Ok(_))

    case GET -> Root / "users" / UserIdVar(id) / "insights" =>
      insights.forUser(id).flatMap(Ok(_))
  }
}
