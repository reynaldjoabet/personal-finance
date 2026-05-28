package finance.truelayer

import finance.domain.*

import cats.effect.*
import cats.syntax.all.*
import io.circe.*
import io.circe.generic.semiauto.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import java.time.{Instant, LocalDate}
import java.util.UUID

/** TrueLayer client (sketch).
  *
  * The real API (https://docs.truelayer.com/) requires per-user OAuth tokens and polls multiple endpoints (`/accounts`,
  * `/accounts/{id}/transactions`). This sketch covers the parts we'd plug into the sync job and shows where the
  * Iron-refined types meet untrusted upstream data.
  */
trait TrueLayer[F[_]] {
  def listAccounts(token: TrueLayer.AccessToken): F[List[TrueLayer.RawAccount]]
  def listTransactions(
      token: TrueLayer.AccessToken,
      accountExternalId: String,
      from: LocalDate,
      to: LocalDate
  ): F[List[TrueLayer.RawTransaction]]
}

object TrueLayer {

  opaque type AccessToken = String
  object AccessToken {
    def apply(s: String): AccessToken = s
    extension (t: AccessToken) def value: String = t
  }

  /** Raw shapes: deliberately *not* Iron-refined — refinement happens when we promote these to our domain types.
    */
  final case class RawAccount(
      account_id: String,
      account_type: String,
      currency: String,
      display_name: String,
      provider: RawProvider,
      balance: Long
  )

  final case class RawProvider(provider_id: String)

  final case class RawTransaction(
      transaction_id: String,
      timestamp: Instant,
      description: String,
      amount: BigDecimal,
      currency: String,
      transaction_category: Option[String]
  )

  given Decoder[RawProvider] = deriveDecoder
  given Decoder[RawAccount] = deriveDecoder
  given Decoder[RawTransaction] = deriveDecoder

  def fromHttp[F[_]: Concurrent](client: Client[F], base: Uri): TrueLayer[F] =
    new TrueLayer[F] {

      private def auth(token: AccessToken) =
        Authorization(Credentials.Token(AuthScheme.Bearer, token))

      def listAccounts(token: AccessToken): F[List[RawAccount]] = {
        val req = Request[F](Method.GET, base / "data" / "v1" / "accounts").putHeaders(auth(token))
        client.expect[Envelope[RawAccount]](req).map(_.results)
      }

      def listTransactions(
          token: AccessToken,
          accountExternalId: String,
          from: LocalDate,
          to: LocalDate
      ): F[List[RawTransaction]] = {
        val req = Request[F](
          Method.GET,
          (base / "data" / "v1" / "accounts" / accountExternalId / "transactions")
            .withQueryParam("from", from.toString)
            .withQueryParam("to", to.toString)
        ).putHeaders(auth(token))
        client.expect[Envelope[RawTransaction]](req).map(_.results)
      }
    }

  private final case class Envelope[A](results: List[A])
  private given [A: Decoder]: Decoder[Envelope[A]] = deriveDecoder

  /** Promote raw API rows to the domain. This is *the* boundary where we apply Iron constraints — any malformed
    * upstream row becomes a `Left`.
    */
  def toDomainAccount(userId: UserId, raw: RawAccount): Either[String, Account] =
    for {
      cur <- Currency.either(raw.currency)
      kind <- raw.account_type.toLowerCase match {
        case "transaction" | "current" => Right(AccountKind.Checking)
        case "savings"                 => Right(AccountKind.Savings)
        case "credit_card" | "credit"  => Right(AccountKind.Credit)
        case other                     => Left(s"unknown TrueLayer account_type: $other")
      }
    } yield Account(
      id = AccountId.assume(UUID.randomUUID()),
      userId = userId,
      provider = "truelayer",
      externalId = raw.account_id,
      kind = kind,
      currency = cur,
      balance = BalanceMinor.assume(raw.balance),
      updatedAt = Instant.now()
    )

  def toDomainTransaction(accountId: AccountId, cur: Currency, raw: RawTransaction): Either[String, Transaction] =
    for {
      m <- Merchant.either(raw.description)
      amt <- AmountMinor.either((raw.amount * 100).toLong)
      cat <- raw.transaction_category.fold[Either[String, Option[CategoryLabel]]](Right(None))(
        CategoryLabel.either(_).map(Some(_))
      )
    } yield Transaction(
      id = TransactionId.assume(UUID.randomUUID()),
      accountId = accountId,
      occurredOn = raw.timestamp.atZone(java.time.ZoneOffset.UTC).toLocalDate,
      amount = amt,
      currency = cur,
      merchant = m,
      category = cat
    )
}
