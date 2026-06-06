package finance.db

import finance.domain.*
import skunk.{Transaction as _, *}
import skunk.codec.all.*
import org.typelevel.twiddles.syntax.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.skunk.*
import java.time.ZoneOffset

/** Skunk codecs for the domain.
  *
  * `iron-skunk` adds an extension `codec.refined[C]: Codec[A :| C]` that decodes via the base codec and then runs the
  * Iron constraint at the boundary. `_.value` (provided by RefinedType) unwraps the opaque type back to its IronType,
  * and `assume` rewraps a known-good value without re-validating.
  */
object Codecs {

  // --- scalars ---

  val email: Codec[Email] =
    varchar(254).refined[EmailConstraint].imap(Email.assume)(_.value)

  val displayName: Codec[DisplayName] =
    varchar(120).refined[Not[Blank] & MaxLength[120]].imap(DisplayName.assume)(_.value)

  val currency: Codec[Currency] =
    bpchar(3).refined[FixedLength[3] & Match["^[A-Z]{3}$"]].imap(Currency.assume)(_.value)

  val merchant: Codec[Merchant] =
    varchar(200).refined[Not[Blank] & MaxLength[200]].imap(Merchant.assume)(_.value)

  val passwordHash: Codec[PasswordHash] =
    varchar(120).refined[Not[Blank] & MaxLength[120]].imap(PasswordHash.assume)(_.value)

  val category: Codec[CategoryLabel] =
    varchar(64).refined[Not[Blank] & MaxLength[64]].imap(CategoryLabel.assume)(_.value)

  val amountMinor: Codec[AmountMinor] =
    int8.imap(AmountMinor.assume)(_.value)

  val balanceMinor: Codec[BalanceMinor] =
    int8.imap(BalanceMinor.assume)(_.value)

  val userId: Codec[UserId] = uuid.imap(UserId.assume)(_.value)
  val accountId: Codec[AccountId] = uuid.imap(AccountId.assume)(_.value)
  val transactionId: Codec[TransactionId] = uuid.imap(TransactionId.assume)(_.value)

  val accountKind: Codec[AccountKind] =
    varchar(16).eimap[AccountKind] {
      case "checking" => Right(AccountKind.Checking)
      case "savings"  => Right(AccountKind.Savings)
      case "credit"   => Right(AccountKind.Credit)
      case other      => Left(s"unknown account kind: $other")
    } {
      case AccountKind.Checking => "checking"
      case AccountKind.Savings  => "savings"
      case AccountKind.Credit   => "credit"
    }

  val instant: Codec[java.time.Instant] =
    timestamptz.imap(_.toInstant)(_.atOffset(ZoneOffset.UTC))

  // --- aggregates ---
  // skunk 1.x's `*:` chain produces right-nested Tuple2 pairs `(A, (B, (C, ...)))`.
  // Case class Mirrors are flat tuples `(A, B, C, ...)`, so `.to[T]` cannot derive
  // an Iso for >2 fields. We bridge with explicit `imap` and nested destructuring.

  val user: Codec[User] =
    (userId *: email *: displayName *: instant).imap { case (id, (em, (dn, ca))) =>
      User(id, em, dn, ca)
    }(u => (u.id, (u.email, (u.displayName, u.createdAt))))

  val account: Codec[Account] =
    (accountId *: userId *: varchar(64) *: varchar(128) *: accountKind *:
      currency *: balanceMinor *: instant).imap { case (id, (uid, (prov, (ext, (k, (cur, (bal, ua))))))) =>
      Account(id, uid, prov, ext, k, cur, bal, ua)
    }(a =>
      (
        a.id,
        (
          a.userId,
          (
            a.provider,
            (a.externalId, (a.kind, (a.currency, (a.balance, a.updatedAt))))
          )
        )
      )
    )

  val transaction: Codec[finance.domain.Transaction] =
    (transactionId *: accountId *: date *: amountMinor *: currency *: merchant *: category.opt).imap {
      case (id, (aid, (occ, (amt, (cur, (mer, cat)))))) =>
        finance.domain.Transaction(id, aid, occ, amt, cur, mer, cat)
    }(t =>
      (
        t.id,
        (
          t.accountId,
          (t.occurredOn, (t.amount, (t.currency, (t.merchant, t.category))))
        )
      )
    )
}
