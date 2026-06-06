package finance.http

import finance.domain.*

import io.circe.*
import io.circe.generic.semiauto.*
import io.github.iltotore.iron.circe.given

import java.time.{Instant, YearMonth}

/** Circe codecs. `iron-circe` derives codecs for the refined opaque newtypes automatically via `RefinedType.Mirror`.
  */
object Json {

  given Encoder[YearMonth] = Encoder.encodeString.contramap(_.toString)
  given Decoder[YearMonth] =
    Decoder.decodeString.emap(s => scala.util.Try(YearMonth.parse(s)).toEither.left.map(_.getMessage))

  given Encoder[AccountKind] = Encoder.encodeString.contramap {
    case AccountKind.Checking => "checking"
    case AccountKind.Savings  => "savings"
    case AccountKind.Credit   => "credit"
  }
  given Decoder[AccountKind] = Decoder.decodeString.emap {
    case "checking" => Right(AccountKind.Checking)
    case "savings"  => Right(AccountKind.Savings)
    case "credit"   => Right(AccountKind.Credit)
    case other      => Left(s"unknown account kind: $other")
  }

  given Encoder[User] = deriveEncoder
  given Encoder[Account] = deriveEncoder
  given Encoder[Transaction] = deriveEncoder
  given Encoder[CategoryTotal] = deriveEncoder
  given Encoder[MonthlySummary] = deriveEncoder
  given Encoder[Insight] = deriveEncoder

  /** Request bodies for the auth endpoints. */
  final case class Register(email: Email, displayName: DisplayName, password: PlainPassword)
  given Decoder[Register] = deriveDecoder

  final case class Login(email: Email, password: PlainPassword)
  given Decoder[Login] = deriveDecoder

  /** Response body: opaque-to-clients JWT plus when it expires. */
  final case class TokenResponse(token: String, expiresAt: Instant)
  given Encoder[TokenResponse] = deriveEncoder

  /** What clients get from `GET /truelayer/connect`: the authorize URL to redirect the user to, plus the CSRF state
    * we'll expect echoed back on the callback.
    */
  final case class ConnectResponse(authorizeUrl: String, state: String)
  given Encoder[ConnectResponse] = deriveEncoder

  final case class SyncResponse(accounts: Int, transactions: Int, skipped: Int)
  given Encoder[SyncResponse] = deriveEncoder
}
