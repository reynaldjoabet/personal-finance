package finance.domain

import java.util.UUID

import com.fasterxml.uuid.impl.TimeBasedEpochGenerator
import com.fasterxml.uuid.Generators

/**
  * The single source of generated UUIDs.
  *
  * These are UUIDv7: a 48-bit millisecond timestamp in the most significant bits, then random bits.
  * Postgres compares `uuid` as sixteen unsigned bytes, so byte order is timestamp order -- unlike
  * the v4 that `UUID.randomUUID` and `gen_random_uuid()` produce, these sort chronologically and
  * primary-key inserts land at the right-hand edge of the index instead of scattering across it.
  *
  * `timeBasedEpochGenerator` increments a counter for repeat calls inside the same millisecond, so
  * the values are strictly monotonic and `ORDER BY id` is exactly insertion order. The alternative,
  * `timeBasedEpochRandomGenerator`, re-randomises those low bits instead, which keeps ordering only
  * to the millisecond and shuffles ids generated within one -- enough for index locality, but it
  * breaks `ORDER BY id` as insertion order and keyset pagination over the id. The counter has room
  * for millions of ids per millisecond before it would throw, so prefer the monotonic one.
  *
  * The generator is stateful and synchronized, so there is exactly one of it. Generating is a side
  * effect: call this where it is already suspended, not in a value definition.
  */
object Uuid {

  private val generator: TimeBasedEpochGenerator =
    Generators.timeBasedEpochGenerator()

  def next(): UUID = generator.generate()

}
