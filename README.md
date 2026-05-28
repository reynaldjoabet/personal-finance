# Personal Finance Assistant
A small Scala 3 backend that demonstrates how to wire **Iron** refinement types
through **Skunk** for a personal-finance domain: users, bank accounts (via
TrueLayer), transactions, monthly analytics, and rule-based "ML" insights.

Domain invariants live in the types: a `Currency` is *always* a 3-letter ISO
code, an `Email` is *always* well-formed, an `AmountMinor` is *never*
`Long.MinValue`. Validation happens once, at the boundary (HTTP, TrueLayer,
or DB decode), and the rest of the code can trust the value.

## Stack

- Scala 3.3.6 — **braces-only** (`-no-indent` compiler flag)
- [Iron](https://github.com/Iltotore/iron) + `iron-skunk` + `iron-circe`
- [Skunk](https://typelevel.org/skunk/) (native Postgres protocol on Cats Effect)
- http4s (Ember server) + Circe
- Cats Effect 3, log4cats / logback

## Iron + Skunk highlights

Every domain value is an opaque newtype refined by an Iron constraint:

```scala
type Email = Email.T
object Email extends RefinedType[String, Not[Blank] & Match["^[^@\s]+@[^@\s]+\.[^@\s]+$"]]
```

Codecs derive from a base Skunk codec via `iron-skunk`'s `.refined[C]`:

```scala
val email: Codec[Email] =
  varchar(254).refined[EmailConstraint].imap(Email.assume)(_.value)
```

That means a malformed value coming back from Postgres is rejected at decode,
not three layers up — and you can't accidentally pass a `Currency` where an
`Email` is expected, even though both are `String` underneath.

## Running

1. Start a local Postgres and create a database:
   ```bash
   createdb finance
   psql finance < src/main/resources/db/schema.sql
   ```

2. Export config and run:
   ```bash
   export DB_USER=postgres
   export DB_PASSWORD=postgres
   export DB_NAME=finance
   sbt run
   ```

3. Try the API:
   ```bash
   curl -X POST localhost:8080/users \
     -H 'content-type: application/json' \
     -d '{"email":"a@b.co","displayName":"Ada"}'

   curl localhost:8080/users/<uuid>/summary/2026-04
   curl localhost:8080/users/<uuid>/insights
   ```

## What's *not* in here

- Authentication. Add JWT or session auth before exposing to real users.
- TrueLayer OAuth flow. The client sketch assumes you already have an access token; the redirect/token-exchange dance is out of scope.
- A real ML model. `Insights` is rule-based; the interface is stable so a model can be swapped in without touching callers.
