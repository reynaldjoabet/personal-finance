package finance.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.time.{Instant, LocalDate}
import java.util.UUID

// ---------- Identifiers (opaque newtypes over UUID/Long) ----------

type UserId = UserId.T
object UserId extends RefinedType[UUID, Pure] {}

type AccountId = AccountId.T
object AccountId extends RefinedType[UUID, Pure] {}

type TransactionId = TransactionId.T
object TransactionId extends RefinedType[UUID, Pure] {}

type CategoryId = CategoryId.T
object CategoryId extends RefinedType[Int, Positive] {}

// ---------- Validated value objects ----------

/** RFC-5322-ish email check (simple but adequate for sign-up). */
type EmailConstraint = Not[Blank] & Match["""^[^@\s]+@[^@\s]+\.[^@\s]+$"""]
type Email = Email.T
object Email extends RefinedType[String, EmailConstraint] {}

/** Display name: 1..120 non-blank chars. */
type DisplayName = DisplayName.T
object DisplayName extends RefinedType[String, Not[Blank] & MaxLength[120]] {}

/** ISO-4217 currency code (3 uppercase letters). */
type Currency = Currency.T
object Currency extends RefinedType[String, FixedLength[3] & Match["^[A-Z]{3}$"]] {}

/** Money stored in minor units (e.g. cents). Negative values represent outflows. No further constraint — the sign
  * carries meaning.
  */
type AmountMinor = AmountMinor.T
object AmountMinor extends RefinedType[Long, Pure] {}

/** Account balance in minor units — may legitimately be negative (overdraft). */
type BalanceMinor = BalanceMinor.T
object BalanceMinor extends RefinedType[Long, Pure] {}

/** Free-text merchant/description, trimmed to a reasonable length. */
type Merchant = Merchant.T
object Merchant extends RefinedType[String, Not[Blank] & MaxLength[200]] {}

/** Category label used when the ML engine assigns a class. */
type CategoryLabel = CategoryLabel.T
object CategoryLabel extends RefinedType[String, Not[Blank] & MaxLength[64]] {}

/** Bcrypt hash string. The constraint is intentionally loose because hash formats vary; we just want a non-blank,
  * bounded String so a malformed row can't sneak past the DB boundary.
  */
type PasswordHash = PasswordHash.T
object PasswordHash extends RefinedType[String, Not[Blank] & MaxLength[120]] {}

/** Plaintext password coming in over the wire. 8..200 chars; everything else is the hashing function's problem. */
type PlainPassword = PlainPassword.T
object PlainPassword extends RefinedType[String, MinLength[8] & MaxLength[200]] {}

// ---------- Aggregates ----------

final case class User(
    id: UserId,
    email: Email,
    displayName: DisplayName,
    createdAt: Instant
)

enum AccountKind {
  case Checking, Savings, Credit
}

final case class Account(
    id: AccountId,
    userId: UserId,
    provider: String, // e.g. "truelayer"
    externalId: String, // bank-side id
    kind: AccountKind,
    currency: Currency,
    balance: BalanceMinor,
    updatedAt: Instant
)

final case class Transaction(
    id: TransactionId,
    accountId: AccountId,
    occurredOn: LocalDate,
    amount: AmountMinor,
    currency: Currency,
    merchant: Merchant,
    category: Option[CategoryLabel]
)

// ---------- Domain summaries returned by analytics ----------

final case class CategoryTotal(category: CategoryLabel, totalMinor: Long, count: Int)

final case class MonthlySummary(
    month: java.time.YearMonth,
    inflowMinor: Long,
    outflowMinor: Long,
    netMinor: Long,
    byCategory: List[CategoryTotal]
)

final case class Insight(
    title: DisplayName,
    body: String,
    estimatedSavingMinor: Option[Long]
)
