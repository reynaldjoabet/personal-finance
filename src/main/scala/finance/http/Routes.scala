package finance.http

import finance.db.*
import finance.domain.*
import finance.service.*
import finance.truelayer.TrueLayer

import cats.effect.*
import cats.syntax.all.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.Http4sDsl

import java.time.YearMonth
import java.util.UUID

/** Bundles together the public routes (health, auth, truelayer callback) and the authenticated routes (everything under
  * `/users/{id}` and `/truelayer/connect`), combining them into one `HttpRoutes[F]`.
  *
  * The authed routes additionally enforce that the path's user id matches the authenticated subject — a token holder
  * can only act on their own resources.
  */
final class Routes[F[_]: Async](
    auth: Auth[F],
    users: Users[F],
    accounts: Accounts[F],
    analytics: Analytics[F],
    insights: Insights[F],
    truelayer: TrueLayer[F],
    tlTokens: TrueLayerTokens[F],
    sync: TrueLayerSync[F],
    tlStates: TlStateStore[F]
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

  private object CodeQP extends QueryParamDecoderMatcher[String]("code")
  private object StateQP extends QueryParamDecoderMatcher[String]("state")

  /** Public routes that don't need a token. */
  private val publicRoutes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "health" =>
      Ok(Map("status" -> "ok").asJson)

    case req @ POST -> Root / "auth" / "register" =>
      req.as[Json.Register].flatMap { r =>
        auth.register(r.email, r.displayName, r.password).flatMap {
          case Right((_, tok))             => Created(Json.TokenResponse(tok.value, tok.expiresAt))
          case Left(Auth.Error.EmailTaken) => Conflict(errorJson("email already registered"))
          case Left(_)                     => InternalServerError(errorJson("could not register"))
        }
      }

    case req @ POST -> Root / "auth" / "login" =>
      req.as[Json.Login].flatMap { l =>
        auth.login(l.email, l.password).flatMap {
          case Right((_, tok)) => Ok(Json.TokenResponse(tok.value, tok.expiresAt))
          case Left(_)         => Forbidden(errorJson("invalid credentials"))
        }
      }

    // TrueLayer redirects the user's browser back here with ?code & ?state.
    // We swap the state for the user it belongs to, exchange the code for tokens, and store them.
    case GET -> Root / "truelayer" / "callback" :? CodeQP(code) +& StateQP(state) =>
      tlStates.take(state).flatMap {
        case None         => BadRequest(errorJson("unknown or expired state"))
        case Some(userId) =>
          truelayer
            .exchangeCode(code)
            .flatMap(tokens => tlTokens.upsert(userId, tokens))
            .flatMap(_ => Ok(Map("connected" -> true).asJson))
            .handleErrorWith(e => BadGateway(errorJson(s"truelayer token exchange failed: ${e.getMessage}")))
      }
  }

  /** Authed routes — the path's `{id}` must equal the JWT subject. */
  private val authedRoutes: AuthedRoutes[UserId, F] = AuthedRoutes.of[UserId, F] {

    case GET -> Root / "users" / UserIdVar(id) as me =>
      requireSelf(id, me) {
        users.find(id).flatMap {
          case Some(u) => Ok(u)
          case None    => NotFound()
        }
      }

    case GET -> Root / "users" / UserIdVar(id) / "accounts" as me =>
      requireSelf(id, me)(accounts.listFor(id).flatMap(Ok(_)))

    case GET -> Root / "users" / UserIdVar(id) / "summary" / MonthVar(m) as me =>
      requireSelf(id, me)(analytics.monthly(id, m).flatMap(Ok(_)))

    case GET -> Root / "users" / UserIdVar(id) / "summary" as me =>
      requireSelf(id, me)(analytics.lastNMonths(id, 6).flatMap(Ok(_)))

    case GET -> Root / "users" / UserIdVar(id) / "insights" as me =>
      requireSelf(id, me)(insights.forUser(id).flatMap(Ok(_)))

    case POST -> Root / "users" / UserIdVar(id) / "sync" as me =>
      requireSelf(id, me) {
        sync
          .syncUser(id)
          .flatMap(r => Ok(Json.SyncResponse(r.accountsUpserted, r.transactionsInserted, r.skipped)))
          .handleErrorWith {
            case e if Option(e.getMessage).exists(_.contains("has not connected")) =>
              Conflict(errorJson("user has not connected TrueLayer; call /truelayer/connect first"))
            case e =>
              BadGateway(errorJson(s"sync failed: ${e.getMessage}"))
          }
      }

    // Start the OAuth dance — returns the URL the client should send the user to.
    case GET -> Root / "truelayer" / "connect" as me =>
      tlStates.mint(me).flatMap { state =>
        val url = truelayer.authorizeUrl(state)
        Ok(Json.ConnectResponse(url.renderString, state))
      }
  }

  val routes: HttpRoutes[F] = publicRoutes <+> AuthMiddleware.secure(auth)(authedRoutes)

  private def errorJson(msg: String): io.circe.Json = Map("error" -> msg).asJson

  private def requireSelf(pathId: UserId, me: UserId)(action: F[Response[F]]): F[Response[F]] =
    if (pathId == me) action else Forbidden(errorJson("not your resource"))
}
