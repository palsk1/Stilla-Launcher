package se.stilla.engine

import java.time.Instant

/**
 * The engine's answer to "this app is coming to the front: allow it, ask, or block?".
 * [step] is which line of the decision order matched (1–7), for the debug log.
 */
sealed interface Verdict {
    val step: Int

    /** 1. Essential: open immediately and never touch it. */
    data object Essential : Verdict {
        override val step = 1
    }

    /** 2–4. Blocked. [until] is when it lifts (null = not known in advance). [label] names the schedule. */
    data class Blocked(
        val reason: BlockReason,
        val until: Instant?,
        val label: String? = null,
        override val step: Int,
    ) : Verdict

    /** 5. Ask "how long?". [waitSec] is the pause before the chips work (launch delay or a soft schedule). */
    data class Prompt(
        val waitSec: Int,
        val opensLeft: Int?,
        val minutesLeftToday: Int?,
        val softSchedule: String? = null,
    ) : Verdict {
        override val step = 5
    }

    /** 6. Inside a session you already chose; [remainingMs] can be ≤ 0 in remind mode. */
    data class Continue(val remainingMs: Long) : Verdict {
        override val step = 6
    }

    /** 7. Not watched: open normally. */
    data object Open : Verdict {
        override val step = 7
    }
}

/** Things the app must react to, from [DecisionEngine.tick]. */
sealed interface EngineEvent {
    val app: AppId

    /** The app in front is no longer allowed (a schedule started, a budget ran out): send it home and show why. */
    data class KickOut(override val app: AppId, val verdict: Verdict.Blocked) : EngineEvent

    /** The minutes you picked are used up. What to do depends on [onTimeOver]. */
    data class TimeUp(
        override val app: AppId,
        val onTimeOver: OnTimeOver,
        /** For [OnTimeOver.EXTEND_MINDFULLY]: how long the card counts down before +5 min works. */
        val extensionWaitSec: Int,
        val minutesToday: Int,
    ) : EngineEvent

    /** Remind mode: time is up and you're still here. */
    data class Remind(override val app: AppId, val overByMs: Long) : EngineEvent
}
