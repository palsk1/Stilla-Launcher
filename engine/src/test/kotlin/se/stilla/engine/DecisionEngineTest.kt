package se.stilla.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

class DecisionEngineTest {

    private val youtube = AppId("com.google.android.youtube")
    private val instagram = AppId("com.instagram.android")
    private val swish = AppId("se.bankgirot.swish")
    private val dialer = AppId("com.samsung.android.dialer")
    private val bankId = AppId("com.bankid.bus")
    private val home = AppId("se.stilla.launcher")

    // Tuesday 6 Oct 2026, 12:00
    private fun setup(at: LocalDateTime = LocalDateTime.of(2026, 10, 6, 12, 0)): Pair<FakeClock, DecisionEngine> {
        val clock = FakeClock(at)
        val engine = DecisionEngine(clock, DecisionEngine.Config(isEssential = { Essentials.isEssential(it.packageName, self = home.packageName) }))
        return clock to engine
    }

    private val night = Schedule(
        id = "night", name = "Night", days = DayOfWeek.entries.toSet(),
        start = LocalTime.of(22, 0), end = LocalTime.of(7, 0), target = Target.AllExceptEssentials,
    )

    // ---- Decision order, one row per rule ----

    @Test fun step1_essentialsAlwaysOpen_evenDuringEverything() {
        val (clock, e) = setup(LocalDateTime.of(2026, 10, 6, 23, 0))
        e.setSchedules(listOf(night))
        e.block(Target.AllExceptEssentials, BlockReason.MANUAL, 30 * 24 * HOUR_MS)
        e.setRules(listOf(WatchRule(dialer, dailyOpenLimit = 0)))
        for (app in listOf(dialer, bankId, home)) assertEquals(Verdict.Essential, e.decide(app))
        assertEquals(2, e.decide(youtube).step)
    }

    @Test fun step2_hardBlockBeatsSchedule() {
        val (_, e) = setup(LocalDateTime.of(2026, 10, 6, 23, 0))
        e.setSchedules(listOf(night))
        e.block(Target.of(youtube), BlockReason.MANUAL, 7 * 24 * HOUR_MS)
        val v = e.decide(youtube) as Verdict.Blocked
        assertEquals(BlockReason.MANUAL, v.reason)
        assertEquals(2, v.step)
    }

    @Test fun step3_hardScheduleBlocksUntilItsEnd() {
        val (_, e) = setup(LocalDateTime.of(2026, 10, 6, 23, 30))
        e.setSchedules(listOf(night))
        val v = e.decide(swish) as Verdict.Blocked
        assertEquals(BlockReason.SCHEDULE, v.reason)
        assertEquals("Night", v.label)
        assertEquals(local(2026, 10, 7, 7, 0), v.until)
    }

    @Test fun step3_softScheduleShowsPromptBehindWait() {
        val (_, e) = setup(LocalDateTime.of(2026, 10, 6, 23, 30))
        e.setSchedules(listOf(night.copy(strictness = Strictness.SOFT)))
        val v = e.decide(swish) as Verdict.Prompt
        assertEquals(60, v.waitSec)
        assertEquals("Night", v.softSchedule)
    }

    @Test fun step4_budgetUsedUpBlocksUntil0400() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube, dailyBudgetMin = 30)))
        e.startSession(youtube, 45)
        e.onForeground(youtube)
        clock.advanceMin(30)
        val v = e.decide(youtube) as Verdict.Blocked
        assertEquals(BlockReason.DAILY_BUDGET, v.reason)
        assertEquals(local(2026, 10, 7, 4, 0), v.until)
    }

    @Test fun step4_openLimit() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(instagram, dailyOpenLimit = 2)))
        repeat(2) {
            e.startSession(instagram, 5)
            e.endSession(instagram)
        }
        val v = e.decide(instagram) as Verdict.Blocked
        assertEquals(BlockReason.OPEN_LIMIT, v.reason)
        clock.setLocal(LocalDateTime.of(2026, 10, 7, 4, 1))
        assertTrue(e.decide(instagram) is Verdict.Prompt)
    }

    @Test fun step5_promptCarriesOpensAndMinutesLeft() {
        val (_, e) = setup()
        e.setRules(listOf(WatchRule(instagram, launchDelaySec = 10, dailyOpenLimit = 5, dailyBudgetMin = 20)))
        e.startSession(instagram, 5)
        e.endSession(instagram)
        val v = e.decide(instagram) as Verdict.Prompt
        assertEquals(10, v.waitSec)
        assertEquals(4, v.opensLeft)
        assertEquals(20, v.minutesLeftToday)
    }

    @Test fun step6_sessionContinues_step7_unwatchedOpens() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 10)
        e.onForeground(youtube)
        clock.advanceMin(4)
        val v = e.decide(youtube) as Verdict.Continue
        assertEquals(6 * MINUTE_MS, v.remainingMs)
        assertEquals(Verdict.Open, e.decide(swish))
    }

    // ---- Sessions ----

    @Test fun shortTripAwayPausesTheSession() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 10)
        e.onForeground(youtube)
        clock.advanceMin(3)
        e.onForeground(AppId("com.samsung.android.messaging")) // copy a code
        clock.advanceSec(60)
        assertEquals(7 * MINUTE_MS, (e.decide(youtube) as Verdict.Continue).remainingMs)
        e.onForeground(youtube)
        clock.advanceMin(2)
        assertEquals(5 * MINUTE_MS, (e.decide(youtube) as Verdict.Continue).remainingMs)
    }

    @Test fun beingAwayPastGraceEndsTheSession() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 10)
        e.onForeground(youtube)
        clock.advanceMin(1)
        e.onForeground(home)
        clock.advanceMin(3)
        assertTrue(e.decide(youtube) is Verdict.Prompt)
        assertNull(e.session(youtube))
    }

    @Test fun sessionStartedFromPromptCountsFromLaunch() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.onForeground(home)
        e.startSession(youtube, 5) // chosen on Stilla's prompt
        clock.advanceSec(1)
        e.onForeground(youtube)
        clock.advanceMin(5)
        assertTrue(e.tick().single() is EngineEvent.TimeUp)
    }

    // ---- When time is over ----

    @Test fun mindfulExtensionWaitDoublesEachTime() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 5)
        e.onForeground(youtube)
        clock.advanceMin(5)
        val first = e.tick().single() as EngineEvent.TimeUp
        assertEquals(OnTimeOver.EXTEND_MINDFULLY, first.onTimeOver)
        assertEquals(30, first.extensionWaitSec)
        assertTrue("sent once only", e.tick().isEmpty())
        e.extend(youtube, 5)
        clock.advanceMin(5)
        assertEquals(60, (e.tick().single() as EngineEvent.TimeUp).extensionWaitSec)
        e.extend(youtube, 5)
        clock.advanceMin(5)
        assertEquals(120, (e.tick().single() as EngineEvent.TimeUp).extensionWaitSec)
    }

    @Test fun exitAndBlock() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube, onTimeOver = OnTimeOver.EXIT_AND_BLOCK, blockMinutes = 20)))
        e.startSession(youtube, 5)
        e.onForeground(youtube)
        clock.advanceMin(5)
        val ev = e.tick().single() as EngineEvent.TimeUp
        assertEquals(OnTimeOver.EXIT_AND_BLOCK, ev.onTimeOver)
        e.onForeground(home)
        val v = e.decide(youtube) as Verdict.Blocked
        assertEquals(BlockReason.TIME_OUT, v.reason)
        clock.advanceMin(20)
        assertTrue(e.decide(youtube) is Verdict.Prompt)
    }

    @Test fun remindModeKeepsAllowingAndRemindsEveryFiveMinutes() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube, onTimeOver = OnTimeOver.REMIND)))
        e.startSession(youtube, 5)
        e.onForeground(youtube)
        clock.advanceMin(5)
        assertTrue(e.tick().single() is EngineEvent.TimeUp)
        clock.advanceMin(2)
        assertTrue(e.tick().isEmpty())
        assertTrue(e.decide(youtube) is Verdict.Continue)
        clock.advanceMin(3)
        assertTrue(e.tick().single() is EngineEvent.Remind)
    }

    @Test fun scheduleStartingWhileInsideKicksYouOut() {
        val (clock, e) = setup(LocalDateTime.of(2026, 10, 6, 21, 55))
        e.setSchedules(listOf(night))
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 15)
        e.onForeground(youtube)
        assertEquals(5 * MINUTE_MS, e.msUntilNextTick())
        clock.advanceMin(5)
        val ev = e.tick().single() as EngineEvent.KickOut
        assertEquals("Night", ev.verdict.label)
    }

    @Test fun budgetCapsTheSessionYouPick() {
        val (_, e) = setup()
        e.setRules(listOf(WatchRule(youtube, dailyBudgetMin = 8)))
        assertEquals(8 * MINUTE_MS, e.startSession(youtube, 15).allowedMs)
    }

    @Test fun winsAreCounted() {
        val (_, e) = setup()
        e.backedOut(youtube)
        e.backedOut(youtube)
        assertEquals(2, e.usageToday(youtube).wins)
    }

    // ---- Days, midnight, daylight saving, tampering ----

    @Test fun lateNightCountsTowardTheDayItStarted() {
        val d = DayRules(java.time.ZoneId.of("Europe/Stockholm"))
        assertEquals(LocalDate.of(2026, 10, 6), d.dayOf(local(2026, 10, 7, 3, 59)))
        assertEquals(LocalDate.of(2026, 10, 7), d.dayOf(local(2026, 10, 7, 4, 0)))
    }

    @Test fun scheduleCrossingMidnight_table() {
        val zone = java.time.ZoneId.of("Europe/Stockholm")
        val weekdays = night.copy(days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))
        data class Row(val at: LocalDateTime, val active: Boolean)
        // 6 Oct 2026 is a Tuesday; 10 Oct a Saturday.
        listOf(
            Row(LocalDateTime.of(2026, 10, 6, 21, 59), false),
            Row(LocalDateTime.of(2026, 10, 6, 22, 0), true),
            Row(LocalDateTime.of(2026, 10, 7, 0, 30), true),
            Row(LocalDateTime.of(2026, 10, 7, 6, 59), true),
            Row(LocalDateTime.of(2026, 10, 7, 7, 0), false),
            Row(LocalDateTime.of(2026, 10, 10, 6, 0), true), // Friday night into Saturday
            Row(LocalDateTime.of(2026, 10, 10, 23, 0), false), // Saturday night: off
            Row(LocalDateTime.of(2026, 10, 11, 6, 0), false), // Sunday morning: off
            Row(LocalDateTime.of(2026, 10, 12, 6, 0), false), // Monday morning: Sunday night was off
        ).forEach { r ->
            val active = weekdays.activeWindow(ZonedDateTime.of(r.at, zone).toInstant(), zone) != null
            assertEquals("at ${r.at}", r.active, active)
        }
        val work = Schedule("w", "Work", setOf(DayOfWeek.TUESDAY), LocalTime.of(9, 0), LocalTime.of(17, 0), Target.of(youtube))
        assertTrue(work.activeWindow(local(2026, 10, 6, 9, 0), zone) != null)
        assertNull(work.activeWindow(local(2026, 10, 6, 17, 0), zone))
        val allDay = work.copy(end = LocalTime.of(9, 0))
        assertTrue(allDay.activeWindow(local(2026, 10, 7, 8, 59), zone) != null)
    }

    @Test fun daylightSavingFallBack_25Oct2026() {
        // Sweden goes from 03:00 back to 02:00 on Sunday 25 October 2026.
        val (clock, e) = setup(LocalDateTime.of(2026, 10, 24, 23, 0))
        e.setSchedules(listOf(night))
        val v = e.decide(youtube) as Verdict.Blocked
        assertEquals(local(2026, 10, 25, 7, 0), v.until)
        // The night is an hour longer in real time: 23:00 → 07:00 is 9 hours.
        assertEquals(9 * HOUR_MS, v.until!!.toEpochMilli() - clock.wall.toEpochMilli())
        clock.advanceMs(9 * HOUR_MS - 1)
        assertTrue(e.decide(youtube) is Verdict.Blocked)
        clock.advanceMs(1)
        assertEquals(Verdict.Open, e.decide(youtube))
        // The day still resets at 04:00 local time.
        assertEquals(LocalDate.of(2026, 10, 24), e.days.dayOf(local(2026, 10, 25, 3, 30)))
        assertEquals(LocalDate.of(2026, 10, 25), e.days.dayOf(local(2026, 10, 25, 4, 0)))
    }

    @Test fun movingTheClockForwardDoesNotEndABlock() {
        val (clock, e) = setup()
        e.block(Target.of(youtube), BlockReason.MANUAL, HOUR_MS)
        clock.tamperWallMin(120)
        assertTrue(e.decide(youtube) is Verdict.Blocked)
        clock.advanceMin(60)
        assertEquals(Verdict.Open, e.decide(youtube))
    }

    @Test fun moving_theClockBackAcrossAReboot_keepsTheBlock() {
        val (clock, e) = setup()
        e.block(Target.of(youtube), BlockReason.MANUAL, HOUR_MS)
        clock.reboot()
        clock.tamperWallMin(-600)
        assertTrue(e.decide(youtube) is Verdict.Blocked)
    }

    @Test fun longBlockSurvivesARebootByTheWallClock() {
        val (clock, e) = setup()
        e.block(Target.of(youtube), BlockReason.MANUAL, 30 * 24 * HOUR_MS)
        clock.advanceMin(60 * 24 * 10)
        clock.reboot()
        val v = e.decide(youtube) as Verdict.Blocked
        // 30 × 24 real hours; summer time ended on the way, so it reads 11:00 on the clock.
        assertEquals(local(2026, 11, 5, 11, 0), v.until)
    }

    @Test fun usageIsCountedPerDay() {
        val (clock, e) = setup(LocalDateTime.of(2026, 10, 7, 3, 0))
        e.onForeground(youtube)
        clock.advanceMin(30)
        e.onForeground(home)
        assertEquals(30 * MINUTE_MS, e.usageToday(youtube).foregroundMs)
        clock.setLocal(LocalDateTime.of(2026, 10, 7, 4, 0))
        assertEquals(0L, e.usageToday(youtube).foregroundMs)
    }


    // ---- Found in review ----

    @Test fun extendingAfterALongWaitOnTheCardKeepsTheSession() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 5)
        e.onForeground(youtube)
        clock.advanceMin(5)
        assertTrue(e.tick().single() is EngineEvent.TimeUp)
        e.onForeground(home) // Stilla's card comes to the front
        clock.advanceMin(3) // longer than the 2-minute grace
        e.extend(youtube, 5)
        e.onForeground(youtube)
        val v = e.decide(youtube) as Verdict.Continue
        assertEquals(5 * MINUTE_MS, v.remainingMs)
        clock.advanceMin(5)
        assertTrue("time-up fires again", e.tick().single() is EngineEvent.TimeUp)
    }

    @Test fun screenOffDoesNotCountOrEndTheSession() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 10)
        e.onForeground(youtube)
        clock.advanceMin(3)
        e.suspend()
        clock.advanceMin(60)
        e.resume()
        e.onForeground(youtube)
        assertEquals(7 * MINUTE_MS, (e.decide(youtube) as Verdict.Continue).remainingMs)
        assertEquals(3 * MINUTE_MS, e.usageToday(youtube).foregroundMs)
    }

    @Test fun doneThenReopenCostsTheExtensionWait() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube)))
        e.startSession(youtube, 5)
        e.onForeground(youtube)
        clock.advanceMin(5)
        e.tick()
        e.endSession(youtube) // Done
        e.onForeground(home)
        assertEquals(30, (e.decide(youtube) as Verdict.Prompt).waitSec)
        clock.advanceMin(31)
        assertEquals(0, (e.decide(youtube) as Verdict.Prompt).waitSec)
    }

    @Test fun snapshotRestoreKeepsSessionsAndCounts() {
        val (clock, e) = setup()
        e.setRules(listOf(WatchRule(youtube, dailyOpenLimit = 3)))
        e.startSession(youtube, 10)
        e.onForeground(youtube)
        clock.advanceMin(4)
        val snap = e.snapshot()
        val e2 = DecisionEngine(clock)
        e2.setRules(listOf(WatchRule(youtube, dailyOpenLimit = 3)))
        e2.restore(snap)
        clock.advanceSec(30) // the process restarts quickly
        e2.onForeground(youtube)
        assertEquals(6 * MINUTE_MS, (e2.decide(youtube) as Verdict.Continue).remainingMs)
        assertEquals(1, e2.usageToday(youtube).opens)
    }

    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), java.time.ZoneId.of("Europe/Stockholm")).toInstant()
}
