package se.stilla.engine

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A clock tests can move, reboot and tamper with. */
class FakeClock(start: LocalDateTime, override val zone: ZoneId = ZoneId.of("Europe/Stockholm")) : EngineClock {
    var wall: Instant = ZonedDateTime.of(start, zone).toInstant()
    var elapsed: Long = 1_000_000L
    var boot: Int = 1

    override fun now() = Moment(wall, elapsed, "boot$boot")

    /** Real time passes: both clocks move. */
    fun advanceMin(min: Long) = advanceMs(min * 60_000)
    fun advanceSec(sec: Long) = advanceMs(sec * 1000)
    fun advanceMs(ms: Long) {
        wall = wall.plusMillis(ms)
        elapsed += ms
    }

    /** You change the clock in Settings: only the wall clock moves. */
    fun tamperWallMin(min: Long) {
        wall = wall.plusSeconds(min * 60)
    }

    fun reboot() {
        boot++
        elapsed = 5_000L
    }

    fun setLocal(t: LocalDateTime) {
        val target = ZonedDateTime.of(t, zone).toInstant()
        advanceMs(target.toEpochMilli() - wall.toEpochMilli())
    }
}
