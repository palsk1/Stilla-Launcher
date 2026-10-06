package se.stilla.engine

import java.time.Instant

/**
 * Tightening a rule is always instant; loosening one always costs time.
 *
 * Any change that loosens (a longer limit, removing a block, deleting a
 * schedule, turning a service off inside Stilla) waits for the cooldown you
 * chose earlier. Each loosening on the same day doubles the next wait.
 * A rule lock forbids loosening entirely until it ends; the only way out is
 * typing [RuleLock.EXIT_SENTENCE] and then waiting [RuleLock.EXIT_WAIT_MS].
 */
object Commitment {

    private fun strength(mode: OnTimeOver): Int = when (mode) {
        OnTimeOver.REMIND -> 0
        OnTimeOver.EXTEND_MINDFULLY -> 1
        OnTimeOver.EXIT_AND_BLOCK -> 2
    }

    /** A null limit means "no limit", the loosest possible. */
    private fun looserLimit(old: Int?, new: Int?): Boolean = when {
        old == null -> false
        new == null -> true
        else -> new > old
    }

    fun isLoosening(old: WatchRule?, new: WatchRule?): Boolean {
        if (old == null) return false // watching a new app only tightens
        if (new == null) return true // un-watching
        return (old.prompt && !new.prompt) ||
            new.launchDelaySec < old.launchDelaySec ||
            strength(new.onTimeOver) < strength(old.onTimeOver) ||
            (old.onTimeOver == OnTimeOver.EXIT_AND_BLOCK && new.onTimeOver == OnTimeOver.EXIT_AND_BLOCK &&
                new.blockMinutes < old.blockMinutes) ||
            looserLimit(old.dailyBudgetMin, new.dailyBudgetMin) ||
            looserLimit(old.dailyOpenLimit, new.dailyOpenLimit)
    }

    fun isLoosening(old: Schedule?, new: Schedule?): Boolean {
        if (old == null) return false
        if (new == null) return true
        if (old.enabled && !new.enabled) return true
        if (!old.enabled) return false // it wasn't doing anything
        if (old.strictness == Strictness.HARD && new.strictness == Strictness.SOFT) return true
        if (!new.days.containsAll(old.days)) return true
        if (!coversAll(new.target, old.target)) return true
        return !windowContains(new, old)
    }

    /** Ending or shortening a block loosens; lengthening it is instant. */
    fun isLoosening(old: Block, newDurationMs: Long?): Boolean = newDurationMs == null || newDurationMs < old.durationMs

    private fun coversAll(new: Target, old: Target): Boolean = when {
        new is Target.AllExceptEssentials -> true
        old is Target.AllExceptEssentials -> false
        else -> (new as Target.Apps).apps.containsAll((old as Target.Apps).apps)
    }

    /** Does [new]'s daily window cover every minute of [old]'s? Checked minute by minute over one day. */
    private fun windowContains(new: Schedule, old: Schedule): Boolean {
        for (m in 0 until 24 * 60) {
            if (inWindow(old, m) && !inWindow(new, m)) return false
        }
        return true
    }

    /** Minute of day [m] is inside the schedule's window, counting from its start day. */
    private fun inWindow(s: Schedule, m: Int): Boolean {
        val start = s.start.hour * 60 + s.start.minute
        val end = s.end.hour * 60 + s.end.minute
        return when {
            start == end -> true
            start < end -> m in start until end
            else -> m >= start || m < end
        }
    }
}

/** "Lock my rules until Sunday 18:00." */
data class RuleLock(val until: Instant) {
    fun isActive(now: Instant): Boolean = now.isBefore(until)

    companion object {
        const val EXIT_SENTENCE = "I am choosing to turn this off"
        const val EXIT_WAIT_MS = 10 * MINUTE_MS

        /** Case, spacing and a final full stop don't matter; the words do. */
        fun matchesExitSentence(typed: String): Boolean {
            fun clean(s: String) = s.trim().trimEnd('.', '!').lowercase().split(Regex("\\s+")).joinToString(" ")
            return clean(typed) == clean(EXIT_SENTENCE)
        }
    }
}

/** A loosening change waiting for its cooldown. [payload] is whatever the app needs to apply it. */
data class PendingChange(
    val id: String,
    val description: String,
    val payload: String,
    val requested: Moment,
    val delayMs: Long,
) {
    fun remainingMs(now: Moment): Long = delayMs - now.msSince(requested)
    fun isDue(now: Moment): Boolean = remainingMs(now) <= 0
}

/** Decides what happens to a requested change. */
class CommitmentPolicy(
    /** The cooldown you chose: 5 minutes to 24 hours. */
    val cooldownMs: Long = 15 * MINUTE_MS,
    val ruleLock: RuleLock? = null,
) {
    init {
        require(cooldownMs in 5 * MINUTE_MS..24 * HOUR_MS) { "Cooldown must be 5 min to 24 h" }
    }

    sealed interface Outcome {
        data object ApplyNow : Outcome
        data class Wait(val delayMs: Long) : Outcome
        data class Locked(val until: Instant) : Outcome
    }

    /**
     * @param looseningsToday how many loosenings you've already made today;
     *   each one doubles the next wait (15, 30, 60 min…), capped at 24 h.
     */
    fun evaluate(loosening: Boolean, now: Moment, looseningsToday: Int): Outcome {
        if (!loosening) return Outcome.ApplyNow
        ruleLock?.let { if (it.isActive(now.wall)) return Outcome.Locked(it.until) }
        val factor = 1L shl looseningsToday.coerceIn(0, 10)
        return Outcome.Wait(minOf(cooldownMs * factor, 24 * HOUR_MS))
    }
}
