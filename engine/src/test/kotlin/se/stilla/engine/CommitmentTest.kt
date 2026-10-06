package se.stilla.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class CommitmentTest {
    private val yt = AppId("com.google.android.youtube")
    private val base = WatchRule(yt, launchDelaySec = 10, dailyBudgetMin = 30, dailyOpenLimit = 5)

    @Test fun watchRules_table() {
        data class Row(val name: String, val new: WatchRule?, val loosens: Boolean)
        listOf(
            Row("unchanged", base, false),
            Row("smaller budget", base.copy(dailyBudgetMin = 20), false),
            Row("bigger budget", base.copy(dailyBudgetMin = 40), true),
            Row("budget removed", base.copy(dailyBudgetMin = null), true),
            Row("fewer opens", base.copy(dailyOpenLimit = 3), false),
            Row("more opens", base.copy(dailyOpenLimit = 6), true),
            Row("shorter delay", base.copy(launchDelaySec = 5), true),
            Row("longer delay", base.copy(launchDelaySec = 20), false),
            Row("prompt off", base.copy(prompt = false), true),
            Row("to exit and block", base.copy(onTimeOver = OnTimeOver.EXIT_AND_BLOCK), false),
            Row("to remind", base.copy(onTimeOver = OnTimeOver.REMIND), true),
            Row("un-watch", null, true),
        ).forEach { assertEquals(it.name, it.loosens, Commitment.isLoosening(base, it.new)) }
        assertFalse("watching a new app", Commitment.isLoosening(null, base))
        val strict = base.copy(onTimeOver = OnTimeOver.EXIT_AND_BLOCK, blockMinutes = 30)
        assertTrue(Commitment.isLoosening(strict, strict.copy(blockMinutes = 10)))
    }

    @Test fun schedules_table() {
        val night = Schedule("n", "Night", DayOfWeek.entries.toSet(), LocalTime.of(22, 0), LocalTime.of(7, 0), Target.AllExceptEssentials)
        data class Row(val name: String, val new: Schedule?, val loosens: Boolean)
        listOf(
            Row("starts earlier", night.copy(start = LocalTime.of(21, 0)), false),
            Row("starts later", night.copy(start = LocalTime.of(23, 0)), true),
            Row("ends later", night.copy(end = LocalTime.of(8, 0)), false),
            Row("ends earlier", night.copy(end = LocalTime.of(6, 0)), true),
            Row("drop Saturday", night.copy(days = night.days - DayOfWeek.SATURDAY), true),
            Row("hard to soft", night.copy(strictness = Strictness.SOFT), true),
            Row("disabled", night.copy(enabled = false), true),
            Row("only some apps", night.copy(target = Target.of(yt)), true),
            Row("deleted", null, true),
        ).forEach { assertEquals(it.name, it.loosens, Commitment.isLoosening(night, it.new)) }
    }

    @Test fun cooldownDoublesEachLooseningToday() {
        val clock = FakeClock(LocalDateTime.of(2026, 10, 6, 12, 0))
        val p = CommitmentPolicy(cooldownMs = 15 * MINUTE_MS)
        assertEquals(CommitmentPolicy.Outcome.ApplyNow, p.evaluate(false, clock.now(), 3))
        assertEquals(CommitmentPolicy.Outcome.Wait(15 * MINUTE_MS), p.evaluate(true, clock.now(), 0))
        assertEquals(CommitmentPolicy.Outcome.Wait(30 * MINUTE_MS), p.evaluate(true, clock.now(), 1))
        assertEquals(CommitmentPolicy.Outcome.Wait(60 * MINUTE_MS), p.evaluate(true, clock.now(), 2))
        assertEquals(CommitmentPolicy.Outcome.Wait(24 * HOUR_MS), p.evaluate(true, clock.now(), 9))
    }

    @Test fun pendingChangeIsDueAfterItsDelay_evenIfTheClockIsMoved() {
        val clock = FakeClock(LocalDateTime.of(2026, 10, 6, 12, 0))
        val change = PendingChange("1", "YouTube budget 30 → 60 min", "{}", clock.now(), 15 * MINUTE_MS)
        clock.tamperWallMin(60)
        assertFalse(change.isDue(clock.now()))
        clock.advanceMin(15)
        assertTrue(change.isDue(clock.now()))
    }

    @Test fun ruleLockBlocksLooseningButNotTightening() {
        val clock = FakeClock(LocalDateTime.of(2026, 10, 6, 12, 0))
        val until = clock.now().wall.plusSeconds(3600)
        val p = CommitmentPolicy(ruleLock = RuleLock(until))
        assertEquals(CommitmentPolicy.Outcome.Locked(until), p.evaluate(true, clock.now(), 0))
        assertEquals(CommitmentPolicy.Outcome.ApplyNow, p.evaluate(false, clock.now(), 0))
        clock.advanceMin(61)
        assertTrue(p.evaluate(true, clock.now(), 0) is CommitmentPolicy.Outcome.Wait)
    }

    @Test fun exitSentence() {
        assertTrue(RuleLock.matchesExitSentence("I am choosing to turn this off"))
        assertTrue(RuleLock.matchesExitSentence("  i am choosing to   turn this off. "))
        assertFalse(RuleLock.matchesExitSentence("turn this off"))
    }

    @Test fun blocks() {
        val clock = FakeClock(LocalDateTime.of(2026, 10, 6, 12, 0))
        val b = Block("1", Target.of(yt), BlockReason.MANUAL, clock.now(), HOUR_MS)
        assertTrue(Commitment.isLoosening(b, null))
        assertTrue(Commitment.isLoosening(b, HOUR_MS / 2))
        assertFalse(Commitment.isLoosening(b, 2 * HOUR_MS))
    }
}
