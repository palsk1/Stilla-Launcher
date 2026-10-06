package se.stilla.engine

import java.time.Instant
import java.time.LocalDate

/**
 * Every smart feature runs through here. Each time an app comes to the front,
 * [decide] answers from rules held in memory, in this order (first match wins):
 *
 *  1. Essentials → open, never touched
 *  2. Hard block active → blocked until its end
 *  3. Schedule active → hard: blocked; soft: prompt behind a wait
 *  4. Daily budget or open limit used up → blocked until the day resets
 *  5. Watched app, no active session → prompt
 *  6. Active session with time left → continue
 *  7. Everything else → open
 *
 * Plain Kotlin, no Android: the app feeds it app switches and the time.
 * Not thread-safe; call it from one thread (the app uses the main thread).
 */
class DecisionEngine(
    private val clock: EngineClock,
    private val config: Config = Config(),
) {
    data class Config(
        /** Days reset at this local hour, so a late night counts toward the day it started. */
        val resetHour: Int = 4,
        /** Leaving a session for longer than this ends it. */
        val graceMs: Long = 2 * MINUTE_MS,
        /** The wait in front of the prompt during a soft schedule. */
        val softScheduleWaitSec: Int = 60,
        /** The first mindful-extension wait; each extension the same day doubles it. */
        val extensionBaseWaitSec: Int = 30,
        /** Remind mode repeats this often. */
        val remindEveryMs: Long = 5 * MINUTE_MS,
        /** Reopening an app this soon after its time ran out costs the same wait as +5 min. */
        val afterTimeUpMs: Long = 30 * MINUTE_MS,
        /** Decides what counts as essential. The app passes the user's list. */
        val isEssential: (AppId) -> Boolean = { Essentials.isEssential(it.packageName) },
    )

    val days = DayRules(clock.zone, config.resetHour)

    // ---- Rules (set by the app from storage) ----

    var rules: Map<AppId, WatchRule> = emptyMap()
        private set
    var schedules: List<Schedule> = emptyList()
        private set
    var blocks: List<Block> = emptyList()
        private set

    fun setRules(list: Collection<WatchRule>) {
        rules = list.associateBy { it.app }
    }

    fun setSchedules(list: List<Schedule>) {
        schedules = list
    }

    fun setBlocks(list: List<Block>) {
        blocks = list
    }

    /** Adds a block starting now. Returns it so the app can store it. */
    fun block(target: Target, reason: BlockReason, durationMs: Long, id: String = newId()): Block {
        val b = Block(id, target, reason, clock.now(), durationMs)
        blocks = blocks + b
        return b
    }

    /** Lifts a block now. Loosening: the app runs this only after the commitment cooldown. */
    fun removeBlock(id: String) {
        blocks = blocks.filterNot { it.id == id }
    }

    /** Drops blocks that have ended. Returns the ones removed, so storage can forget them. */
    fun pruneBlocks(): List<Block> {
        val now = clock.now()
        val (active, ended) = blocks.partition { it.isActive(now) }
        blocks = active
        return ended
    }

    // ---- Live state ----

    private val sessions = HashMap<AppId, Session>()
    private val usage = HashMap<Pair<LocalDate, AppId>, DailyUsage>()
    private var front: AppId? = null
    private var frontSince: Moment? = null
    private val lastTimeUp = HashMap<AppId, Moment>()
    private var suspendedAt: Moment? = null

    /** The app currently in front, as last reported. */
    val foreground: AppId? get() = front

    fun session(app: AppId): Session? = sessions[app]

    fun usageToday(app: AppId): DailyUsage {
        val now = clock.now()
        val base = usage[days.dayOf(now.wall) to app] ?: DailyUsage()
        // Include the stretch the app has been in front right now.
        val live = if (front == app) frontSince?.let { now.msSince(it).coerceAtLeast(0) } ?: 0L else 0L
        return base.copy(foregroundMs = base.foregroundMs + live)
    }

    /** Times today you backed out of the prompt, across all apps. */
    fun winsToday(): Int {
        val today = days.dayOf(clock.now().wall)
        return usage.entries.filter { it.key.first == today }.sumOf { it.value.wins }
    }

    /** Lets the app seed today's totals from UsageStatsManager after a restart. */
    fun seedUsage(day: LocalDate, app: AppId, value: DailyUsage) {
        usage[day to app] = value
    }

    // ---- The decision ----

    fun decide(app: AppId): Verdict {
        val now = clock.now()

        // 1. Essentials
        if (config.isEssential(app)) return Verdict.Essential

        // 2. Hard blocks (the one that ends last wins, so the screen shows the real end)
        blocks.filter { it.target.covers(app) && it.isActive(now) }
            .maxByOrNull { it.remainingMs(now) }
            ?.let { return Verdict.Blocked(it.reason, it.endsAtFrom(now), step = 2) }

        // 3. Schedules
        var soft: Pair<String, Instant>? = null
        for (s in schedules) {
            if (!s.target.covers(app)) continue
            val window = s.activeWindow(now.wall, clock.zone) ?: continue
            if (s.strictness == Strictness.HARD) {
                return Verdict.Blocked(BlockReason.SCHEDULE, window.endInstant, s.name, step = 3)
            }
            if (soft == null) soft = s.name to window.endInstant
        }

        val rule = rules[app]
        val session = liveSession(app, now)
        val today = usageToday(app)

        // 4. Budget and open limit
        if (rule != null) {
            val budget = rule.dailyBudgetMin
            if (budget != null && today.foregroundMs >= budget * MINUTE_MS) {
                return Verdict.Blocked(BlockReason.DAILY_BUDGET, days.nextReset(now.wall), step = 4)
            }
            val limit = rule.dailyOpenLimit
            if (limit != null && session == null && today.opens >= limit) {
                return Verdict.Blocked(BlockReason.OPEN_LIMIT, days.nextReset(now.wall), step = 4)
            }
        }

        // 6 before 5: a session you already chose carries on (even into a soft schedule).
        if (session != null) {
            val remaining = session.remainingMs(now)
            if (remaining > 0 || rule?.onTimeOver == OnTimeOver.REMIND) return Verdict.Continue(remaining)
        }

        // 5. Prompt
        if ((rule != null && rule.prompt) || soft != null) {
            // Done → reopen straight away must not be quicker than waiting for +5 min.
            val recentTimeUp = lastTimeUp[app]?.let { now.msSince(it) in 0 until config.afterTimeUpMs } == true
            val wait = maxOf(
                rule?.launchDelaySec ?: 0,
                if (soft != null) config.softScheduleWaitSec else 0,
                if (recentTimeUp) extensionWaitSec(app) else 0,
            )
            return Verdict.Prompt(
                waitSec = wait,
                opensLeft = rule?.dailyOpenLimit?.let { (it - today.opens).coerceAtLeast(0) },
                minutesLeftToday = rule?.dailyBudgetMin?.let { ((it * MINUTE_MS - today.foregroundMs) / MINUTE_MS).toInt().coerceAtLeast(0) },
                softSchedule = soft?.first,
            )
        }

        // 7. Everything else
        return Verdict.Open
    }

    // ---- What the app reports ----

    /** An app came to the front (Stilla itself, or home, counts as an app too). */
    fun onForeground(app: AppId?) {
        val now = clock.now()
        if (app == front) return
        closeFrontStretch(now)
        if (app != null) liveSession(app, now) // ends it if you were away past the grace period
        front = app
        frontSince = now
        if (app != null) {
            sessions[app]?.let { sessions[app] = it.resume(now) }
        }
    }

    /** You picked [minutes] at the prompt. Counts as an open. Capped by what's left of the daily budget. */
    fun startSession(app: AppId, minutes: Int): Session {
        val now = clock.now()
        val rule = rules[app]
        var allowed = minutes * MINUTE_MS
        rule?.dailyBudgetMin?.let { budget ->
            val left = budget * MINUTE_MS - usageToday(app).foregroundMs
            allowed = minOf(allowed, left.coerceAtLeast(0))
        }
        val inFront = front == app
        val s = Session(
            app = app,
            started = now,
            allowedMs = allowed,
            chosenMinutes = minutes,
            inFrontSince = if (inFront) now else null,
            leftAt = if (inFront) null else now,
        )
        sessions[app] = s
        bump(app, now) { it.copy(opens = it.opens + 1) }
        return s
    }

    /** You backed out of the prompt: a win. */
    fun backedOut(app: AppId) {
        bump(app, clock.now()) { it.copy(wins = it.wins + 1) }
    }

    /** How long the extension card should count down before +minutes works, given today's extensions. */
    fun extensionWaitSec(app: AppId): Int {
        val n = usageToday(app).extensions.coerceAtMost(10)
        return config.extensionBaseWaitSec shl n
    }

    /** +[minutes] after the extension card. Doubles the next wait today. */
    fun extend(app: AppId, minutes: Int) {
        val now = clock.now()
        val s = sessions[app] ?: return
        sessions[app] = s.copy(
            allowedMs = s.allowedMs + minutes * MINUTE_MS,
            timeUpSent = false,
            extensions = s.extensions + 1,
            // The time spent on the extension card must not count as "away" and end the session.
            leftAt = if (s.inFrontSince == null) now else null,
        )
        bump(app, now) { it.copy(extensions = it.extensions + 1) }
    }

    /** "Done", or the app was sent home. */
    fun endSession(app: AppId): Session? {
        val now = clock.now()
        if (front == app) closeFrontStretch(now).also { frontSince = now }
        return sessions.remove(app)
    }

    /**
     * Call when an app switch happens and on a timer while a session runs.
     * Returns what the app must do now. [msUntilNextTick] says when to call again.
     */
    fun tick(): List<EngineEvent> {
        val now = clock.now()
        pruneBlocks()
        val app = front ?: return emptyList()
        val events = ArrayList<EngineEvent>()

        val verdict = decide(app)
        if (verdict is Verdict.Blocked) {
            events += EngineEvent.KickOut(app, verdict)
            return events
        }

        val s = sessions[app] ?: return events
        val rule = rules[app]
        val remaining = s.remainingMs(now)
        if (remaining <= 0) {
            val mode = rule?.onTimeOver ?: OnTimeOver.EXTEND_MINDFULLY
            if (!s.timeUpSent) {
                sessions[app] = s.copy(timeUpSent = true, lastRemind = now)
                lastTimeUp[app] = now
                if (mode == OnTimeOver.EXIT_AND_BLOCK) {
                    block(Target.of(app), BlockReason.TIME_OUT, (rule?.blockMinutes ?: 15) * MINUTE_MS)
                    endSession(app)
                }
                events += EngineEvent.TimeUp(
                    app = app,
                    onTimeOver = mode,
                    extensionWaitSec = extensionWaitSec(app),
                    minutesToday = (usageToday(app).foregroundMs / MINUTE_MS).toInt(),
                )
            } else if (mode == OnTimeOver.REMIND) {
                val last = s.lastRemind
                if (last == null || now.msSince(last) >= config.remindEveryMs) {
                    sessions[app] = s.copy(lastRemind = now)
                    events += EngineEvent.Remind(app, -remaining)
                }
            }
        }
        return events
    }

    /** When [tick] next has something to do for the app in front, in ms; null = only on the next app switch. */
    fun msUntilNextTick(): Long? {
        val now = clock.now()
        val app = front ?: return null
        val candidates = ArrayList<Long>()
        sessions[app]?.let { s ->
            val r = s.remainingMs(now)
            if (r > 0) candidates += r
            else if (rules[app]?.onTimeOver == OnTimeOver.REMIND) {
                candidates += s.lastRemind?.let { config.remindEveryMs - now.msSince(it) } ?: 0L
            }
        }
        rules[app]?.dailyBudgetMin?.let { budget ->
            val left = budget * MINUTE_MS - usageToday(app).foregroundMs
            if (left > 0) candidates += left
        }
        if (!config.isEssential(app)) {
            for (s in schedules) {
                if (!s.target.covers(app)) continue
                s.nextStart(now.wall, clock.zone)?.let { candidates += it.toEpochMilli() - now.wall.toEpochMilli() }
            }
        }
        return candidates.filter { it >= 0 }.minOrNull()
    }

    // ---- Screen off ----

    /** The screen went off: nothing counts, and no session ends, until [resume]. */
    fun suspend() {
        val now = clock.now()
        if (suspendedAt != null) return
        closeFrontStretch(now)
        front = null
        frontSince = null
        suspendedAt = now
    }

    /** The phone is unlocked again. Time with the screen off is skipped for every session. */
    fun resume() {
        val now = clock.now()
        val since = suspendedAt ?: return
        suspendedAt = null
        val off = now.msSince(since).coerceAtLeast(0)
        for ((app, s) in sessions.toMap()) {
            sessions[app] = s.copy(
                leftAt = s.leftAt?.plusMs(off),
                lastRemind = s.lastRemind?.plusMs(off),
            )
        }
    }

    val isSuspended: Boolean get() = suspendedAt != null

    // ---- Saving and restoring (so a restart of Stilla doesn't lose your session) ----

    data class Snapshot(
        val sessions: List<Session>,
        val usage: Map<Pair<LocalDate, AppId>, DailyUsage>,
        val lastTimeUp: Map<AppId, Moment>,
    )

    /** Today's and yesterday's usage plus open sessions; older days are dropped. */
    fun snapshot(): Snapshot {
        val now = clock.now()
        closeFrontStretch(now)
        frontSince = if (front != null) now else null
        front?.let { app -> sessions[app]?.let { sessions[app] = it.resume(now) } }
        val today = days.dayOf(now.wall)
        return Snapshot(
            sessions = sessions.values.toList(),
            usage = usage.filterKeys { (day, _) -> !day.isBefore(today.minusDays(1)) },
            lastTimeUp = lastTimeUp.toMap(),
        )
    }

    /**
     * Brings back what [snapshot] saved. Sessions come back paused (as if you
     * had just left the app), so the grace period decides whether they still count.
     */
    fun restore(s: Snapshot) {
        val now = clock.now()
        sessions.clear()
        for (session in s.sessions) {
            sessions[session.app] = session.pause(now).let { if (it.leftAt == null) it.copy(leftAt = now) else it }
        }
        usage.clear()
        usage.putAll(s.usage)
        lastTimeUp.clear()
        lastTimeUp.putAll(s.lastTimeUp)
    }

    // ---- Internals ----

    /** The session for [app], unless you've been away longer than the grace period. */
    private fun liveSession(app: AppId, now: Moment): Session? {
        val s = sessions[app] ?: return null
        val left = s.leftAt
        if (suspendedAt == null && front != app && left != null && now.msSince(left) > config.graceMs) {
            sessions.remove(app)
            return null
        }
        return s
    }

    private fun closeFrontStretch(now: Moment) {
        val app = front ?: return
        val since = frontSince ?: return
        val ms = now.msSince(since).coerceAtLeast(0)
        bump(app, since) { it.copy(foregroundMs = it.foregroundMs + ms) }
        sessions[app]?.let { sessions[app] = it.pause(now) }
    }

    private inline fun bump(app: AppId, at: Moment, change: (DailyUsage) -> DailyUsage) {
        val key = days.dayOf(at.wall) to app
        usage[key] = change(usage[key] ?: DailyUsage())
    }

    private var idCounter = 0L
    private fun newId(): String = "b${clock.now().wall.toEpochMilli()}-${idCounter++}"
}

/** One app's totals for one day. */
data class DailyUsage(
    val foregroundMs: Long = 0,
    val opens: Int = 0,
    val wins: Int = 0,
    val extensions: Int = 0,
)

/** The minutes you picked for one app, and how much of them you've used. */
data class Session(
    val app: AppId,
    val started: Moment,
    val allowedMs: Long,
    val chosenMinutes: Int,
    val usedMs: Long = 0,
    val inFrontSince: Moment? = null,
    val leftAt: Moment? = null,
    val extensions: Int = 0,
    val timeUpSent: Boolean = false,
    val lastRemind: Moment? = null,
) {
    fun usedAt(now: Moment): Long = usedMs + (inFrontSince?.let { now.msSince(it).coerceAtLeast(0) } ?: 0L)
    fun remainingMs(now: Moment): Long = allowedMs - usedAt(now)

    internal fun pause(now: Moment): Session =
        if (inFrontSince == null) this else copy(usedMs = usedAt(now), inFrontSince = null, leftAt = now)

    internal fun resume(now: Moment): Session =
        if (inFrontSince != null) this else copy(inFrontSince = now, leftAt = null)
}
