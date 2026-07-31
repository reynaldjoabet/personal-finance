package finance.http

import java.time.Instant

import cats.effect.*
import cats.effect.std.{Random, SecureRandom}
import cats.syntax.all.*

import finance.domain.UserId

/**
  * In-memory CSRF-state store for the TrueLayer OAuth dance.
  *
  * When the client hits `/truelayer/connect` we mint a random opaque token, remember the (token ->
  * userId) mapping for a few minutes, and ask TrueLayer to echo it back on the callback. On
  * `/truelayer/callback` we `take` the token: the mapping is single-use, so a replay can't bind a
  * stolen code to another user.
  *
  * In-memory is fine for a single-instance demo; behind a load balancer you'd swap this for Redis
  * or a small DB table with a TTL.
  */
trait TlStateStore[F[_]] {

  def mint(userId: UserId): F[String]
  def take(state: String): F[Option[UserId]]

}

object TlStateStore {

  private final case class Entry(userId: UserId, expiresAt: Instant)

  private val ttlSeconds: Long = 10L * 60L // 10 minutes
  private val tokenBytes: Int  = 32

  def make[F[_]: Async]: F[TlStateStore[F]] =
    for {
      rng <- SecureRandom.javaSecuritySecureRandom[F]
      ref <- Ref.of[F, Map[String, Entry]](Map.empty)
    } yield new TlStateStore[F] {

      def mint(userId: UserId): F[String] =
        for {
          token <- randomToken(rng)
          now   <- Async[F].realTimeInstant
          _     <-
            ref.update(m => prune(m, now) + (token -> Entry(userId, now.plusSeconds(ttlSeconds))))
        } yield token

      def take(state: String): F[Option[UserId]] =
        for {
          now   <- Async[F].realTimeInstant
          taken <- ref.modify { m =>
                     val pruned = prune(m, now)
                     pruned.get(state) match {
                       case Some(e) if e.expiresAt.isAfter(now) => (pruned - state, Some(e.userId))
                       case _                                   => (pruned - state, None)
                     }
                   }
        } yield taken

      private def prune(m: Map[String, Entry], now: Instant): Map[String, Entry] =
        m.filter { case (_, e) => e.expiresAt.isAfter(now) }
    }

  private def randomToken[F[_]: Sync](rng: Random[F]): F[String] =
    rng
      .nextBytes(tokenBytes)
      .map(b => java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(b))

}
