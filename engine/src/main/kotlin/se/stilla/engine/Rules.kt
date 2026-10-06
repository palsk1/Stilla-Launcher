package se.stilla.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** An app in one profile. A work-profile copy of an app gets its own rules. */
data class AppId(val packageName: String, val profile: Long = 0) {
    override fun toString(): String = if (profile == 0L) packageName else "$packageName#$profile"
}

/** Which apps a block or schedule covers. */
sealed interface Target {
    fun covers(app: AppId): Boolean

    data class Apps(val apps: Set<AppId>) : Target {
        override fun covers(app: AppId) = app in apps
    }

    /** Every app except the essentials (they are checked before any target). */
    data object AllExceptEssentials : Target {
        override fun covers(app: AppId) = true
    }

    companion object {
        fun of(vararg apps: AppId): Target = Apps(apps.toSet())
    }
}

/** What happens when the minutes you picked run out. */
enum class OnTimeOver {
    /** A full-screen card counts down, then offers +5 min or Done. Each extension today doubles the wait. */
    EXTEND_MINDFULLY,

    /** Sends you home and blocks the app for [WatchRule.blockMinutes]. */
    EXIT_AND_BLOCK,

    /** A short vibration and overlay, every few minutes. */
    REMIND,
}

/** One watched app. Apps without a rule open normally. */
data class WatchRule(
    val app: AppId,
    /** Ask "how long?" before it opens. */
    val prompt: Boolean = true,
    /** A breathing pause before the time chips can be tapped. 0 = none. */
    val launchDelaySec: Int = 0,
    val onTimeOver: OnTimeOver = OnTimeOver.EXTEND_MINDFULLY,
    /** For [OnTimeOver.EXIT_AND_BLOCK]. */
    val blockMinutes: Int = 15,
    /** Total minutes per day across all sessions. null = no budget. */
    val dailyBudgetMin: Int? = null,
    /** Maximum opens (sessions) per day. null = no limit. */
    val dailyOpenLimit: Int? = null,
)

enum class BlockReason { MANUAL, TIME_OUT, SCHEDULE, DAILY_BUDGET, OPEN_LIMIT }

/**
 * A hard block that started at [start] and lasts [durationMs]. Measured with
 * the tamper-proof clock within one boot; across a reboot, by the wall clock.
 * A wall clock moved back across a reboot keeps the block (negative elapsed).
 */
data class Block(
    val id: String,
    val target: Target,
    val reason: BlockReason,
    val start: Moment,
    val durationMs: Long,
) {
    fun remainingMs(now: Moment): Long = durationMs - now.msSince(start)
    fun isActive(now: Moment): Boolean = remainingMs(now) > 0
    fun endsAt(): Instant = start.wall.plusMillis(durationMs)

    /** Where the block will end, as seen from [now] (follows the tamper-proof clock). */
    fun endsAtFrom(now: Moment): Instant = now.wall.plusMillis(remainingMs(now).coerceAtLeast(0))
}

enum class Strictness {
    /** The prompt still appears, behind a wait. */
    SOFT,

    /** No way in until it ends. */
    HARD,
}

/**
 * A named, repeating block, e.g. Night 22:00–07:00. Times are local clock
 * times; an end at or before the start means it crosses midnight, and
 * start == end means all day. [days] are the days the window STARTS on.
 */
data class Schedule(
    val id: String,
    val name: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val target: Target,
    val strictness: Strictness = Strictness.HARD,
    val enabled: Boolean = true,
) {
    val crossesMidnight: Boolean get() = !end.isAfter(start)

    /** The window containing [now], or null if the schedule isn't active. */
    fun activeWindow(now: Instant, zone: ZoneId): ClosedWindow? {
        if (!enabled || days.isEmpty()) return null
        val local = now.atZone(zone).toLocalDateTime()
        for (startDay in listOf(local.toLocalDate().minusDays(1), local.toLocalDate())) {
            if (startDay.dayOfWeek !in days) continue
            val s = startDay.atTime(start)
            val e = if (crossesMidnight) startDay.plusDays(1).atTime(end) else startDay.atTime(end)
            if (!local.isBefore(s) && local.isBefore(e)) return ClosedWindow(s, e, zone)
        }
        return null
    }

    /** The next time this schedule starts, strictly after [now]; null if never. */
    fun nextStart(now: Instant, zone: ZoneId): Instant? {
        if (!enabled || days.isEmpty()) return null
        val local = now.atZone(zone).toLocalDateTime()
        for (i in 0..7L) {
            val day = local.toLocalDate().plusDays(i)
            if (day.dayOfWeek !in days) continue
            val s = ZonedDateTime.of(day.atTime(start), zone).toInstant()
            if (s.isAfter(now)) return s
        }
        return null
    }

    data class ClosedWindow(val start: LocalDateTime, val end: LocalDateTime, val zone: ZoneId) {
        val endInstant: Instant get() = ZonedDateTime.of(end, zone).toInstant()
    }
}
