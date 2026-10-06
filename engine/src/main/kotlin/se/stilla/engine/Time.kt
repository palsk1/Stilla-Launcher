package se.stilla.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A point in time, seen two ways:
 *  - [wall]: the phone's clock, which you can change in Settings
 *  - [elapsedMs]: time since boot (SystemClock.elapsedRealtime), which you can't
 * plus [bootId], so we know whether two moments are from the same boot.
 *
 * Durations are measured with the tamper-proof clock whenever both moments
 * come from the same boot, so moving the clock forward can't end a block.
 */
data class Moment(val wall: Instant, val elapsedMs: Long, val bootId: String) {

    /** Milliseconds from [earlier] to this moment. Can be negative if the wall clock was moved back across a reboot. */
    fun msSince(earlier: Moment): Long =
        if (bootId == earlier.bootId) elapsedMs - earlier.elapsedMs
        else Duration.between(earlier.wall, wall).toMillis()

    fun plusMs(ms: Long): Moment = copy(wall = wall.plusMillis(ms), elapsedMs = elapsedMs + ms)
}

/** Where the engine gets the time. The app passes the real clocks; tests pass a fake. */
interface EngineClock {
    fun now(): Moment
    val zone: ZoneId
}

/**
 * Days start at [resetHour] (04:00 by default), so a late night counts toward
 * the day it started. Uses local clock time, so daylight-saving changes don't
 * move the reset.
 */
class DayRules(val zone: ZoneId, val resetHour: Int = 4) {
    init {
        require(resetHour in 0..23)
    }

    fun dayOf(wall: Instant): LocalDate =
        wall.atZone(zone).toLocalDateTime().minusHours(resetHour.toLong()).toLocalDate()

    /** The first reset strictly after [wall]. */
    fun nextReset(wall: Instant): Instant {
        val day = dayOf(wall)
        return ZonedDateTime.of(day.plusDays(1).atTime(resetHour, 0), zone).toInstant()
    }
}

internal const val MINUTE_MS = 60_000L
internal const val HOUR_MS = 60 * MINUTE_MS
