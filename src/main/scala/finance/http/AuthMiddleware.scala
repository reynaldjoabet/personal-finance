package finance.http

import cats.*
import cats.data.{Kleisli, OptionT}
import cats.syntax.all.*

import finance.domain.UserId
import finance.service.Auth
import org.http4s.*
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware as Http4sAuthMiddleware
import org.typelevel.ci.*

/**
  * Bearer-token middleware. Extracts the JWT from `Authorization: Bearer <token>`, asks the `Auth`
  * service to verify it, and exposes the authenticated `UserId` to downstream routes via http4s'
  * `AuthedRoutes`.
  *
  * A missing or invalid header / token short-circuits to 401 with a `WWW-Authenticate: Bearer`
  * challenge.
  */
object AuthMiddleware {

  /**
    * Wrap `AuthedRoutes[UserId, F]` so they only run for requests carrying a valid bearer JWT,
    * returning 401 otherwise.
    */
  def secure[F[_]: Monad](auth: Auth[F])(authed: AuthedRoutes[UserId, F]): HttpRoutes[F] = {

    val authUser: Kleisli[F, Request[F], Either[String, UserId]] =
      Kleisli { (req: Request[F]) =>
        extractToken(req) match {
          case None        => Monad[F].pure(Left("missing token"): Either[String, UserId])
          case Some(token) => auth.verify(token).map(_.left.map(_ => "invalid token"))
        }
      }

    val onFailure: AuthedRoutes[String, F] = Kleisli { req =>
      OptionT.pure[F](
        Response[F](Status.Unauthorized)
          .putHeaders(Header.Raw(ci"WWW-Authenticate", "Bearer"))
          .withEntity(s"""{"error":"${req.context}"}""")
      )
    }

    val middleware = Http4sAuthMiddleware[F, String, UserId](authUser, onFailure)
    middleware(authed)
  }

  private def extractToken[F[_]](req: Request[F]): Option[String] =
    req.headers.get[Authorization].flatMap { h =>
      h.credentials match {
        case Credentials.Token(AuthScheme.Bearer, t) => Some(t)
        case _                                       => None
      }
    }

}
