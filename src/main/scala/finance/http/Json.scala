package finance.http

import finance.domain.*

import io.circe.*
import io.circe.generic.semiauto.*
import io.github.iltotore.iron.circe.given

import java.time.YearMonth

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

  final case class CreateUser(email: Email, displayName: DisplayName)
  given Decoder[CreateUser] = deriveDecoder
}
