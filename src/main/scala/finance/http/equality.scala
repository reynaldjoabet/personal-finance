package finance.http

import org.http4s.{AuthScheme, Method, Uri}
import org.typelevel.ci.CIString

/**
  * `CanEqual` instances for third-party types, required by `-language:strictEquality`.
  *
  * The http4s routing DSL compares a `Method` and a `Uri.Path` behind every `case GET -> Root /
  * "health"`, and `Credentials.Token` matches on an `AuthScheme`. http4s ships no `CanEqual`
  * instances and we cannot add `derives` to its types, so they live here. Our own types derive
  * theirs at the definition site instead.
  */
given CanEqual[Method, Method] = CanEqual.derived

given CanEqual[Uri.Path, Uri.Path] = CanEqual.derived

given CanEqual[CIString, AuthScheme] = CanEqual.derived
