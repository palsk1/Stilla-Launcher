package se.stilla.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import se.stilla.engine.AppId
import se.stilla.engine.Block
import se.stilla.engine.BlockReason
import se.stilla.engine.Distractions
import se.stilla.engine.Moment
import se.stilla.engine.OnTimeOver
import se.stilla.engine.Schedule
import se.stilla.engine.Strictness
import se.stilla.engine.Target
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/** Which apps a schedule covers. Kept as a choice, so it follows your lists as they change. */
enum class ScheduleScope {
    /** The apps that ask "how long?" (time-wasters). */
    WATCHED,

    /** Every app in Stilla's list except the essentials. */
    ALL,
}

/** A schedule as you set it up. The engine gets it with [scope] turned into a list of apps. */
data class SavedSchedule(
    val schedule: Schedule,
    val scope: ScheduleScope,
) {
    val id: String get() = schedule.id

    /** For the engine and for comparing two versions: the apps this schedule covers right now. */
    fun resolved(watched: Set<AppId>, all: Set<AppId>): Schedule =
        schedule.copy(target = Target.Apps(if (scope == ScheduleScope.ALL) all else watched))
}

/**
 * Which apps get "how long?", what happens when time is over, active blocks and schedules.
 *
 * Time-wasters are picked automatically (see [Distractions]); [added] and
 * [removed] are your own changes on top of that.
 */
data class RulesState(
    val added: Set<AppId> = emptySet(),
    val removed: Set<AppId> = emptySet(),
    val onTimeOver: OnTimeOver = OnTimeOver.EXTEND_MINDFULLY,
    val blocks: List<Block> = emptyList(),
    val schedules: List<SavedSchedule> = emptyList(),
) {
    /** The apps that get the prompt: suggested ones plus yours, minus the ones you turned off. */
    fun watchedAmong(apps: List<RawApp>): Set<AppId> {
        val suggested = apps.asSequence()
            .filter { Distractions.isSuggested(it.key.packageName, it.category) }
            .map { AppId(it.key.packageName, it.key.userSerial) }
            .toSet()
        return (suggested + added) - removed
    }
}

/**
 * Stores the mindful rules. SharedPreferences for this first slice; moves to
 * Room with schedules, budgets and the session log.
 */
class RuleStore(context: Context) {
    private val sp = context.getSharedPreferences("stilla_rules", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<RulesState> = _state.asStateFlow()

    fun setWatched(app: AppId, watched: Boolean) = update {
        if (watched) it.copy(added = it.added + app, removed = it.removed - app)
        else it.copy(added = it.added - app, removed = it.removed + app)
    }

    fun setOnTimeOver(mode: OnTimeOver) = update { it.copy(onTimeOver = mode) }

    fun setBlocks(blocks: List<Block>) = update { it.copy(blocks = blocks) }

    /** Adds the schedule, or replaces the one with the same id. */
    fun saveSchedule(saved: SavedSchedule) = update { s ->
        val i = s.schedules.indexOfFirst { it.id == saved.id }
        s.copy(schedules = if (i < 0) s.schedules + saved else s.schedules.toMutableList().also { it[i] = saved })
    }

    fun deleteSchedule(id: String) = update { s -> s.copy(schedules = s.schedules.filterNot { it.id == id }) }

    @Synchronized
    private fun update(change: (RulesState) -> RulesState) {
        val old = _state.value
        val new = change(old)
        if (new == old) return
        sp.edit()
            .putStringSet(K_ADDED, new.added.map(::encodeApp).toSet())
            .putStringSet(K_REMOVED, new.removed.map(::encodeApp).toSet())
            .putString(K_TIME_OVER, new.onTimeOver.name)
            .putString(K_BLOCKS, new.blocks.joinToString("\n", transform = ::encodeBlock))
            .putString(K_SCHEDULES, new.schedules.joinToString("\n", transform = ::encodeSchedule))
            .apply()
        _state.value = new
    }

    private fun read() = RulesState(
        added = sp.getStringSet(K_ADDED, null).orEmpty().mapNotNull(::decodeApp).toSet(),
        removed = sp.getStringSet(K_REMOVED, null).orEmpty().mapNotNull(::decodeApp).toSet(),
        onTimeOver = runCatching { OnTimeOver.valueOf(sp.getString(K_TIME_OVER, null) ?: "") }
            .getOrDefault(OnTimeOver.EXTEND_MINDFULLY),
        blocks = sp.getString(K_BLOCKS, null).orEmpty().split('\n').mapNotNull(::decodeBlock),
        schedules = sp.getString(K_SCHEDULES, null).orEmpty().split('\n').mapNotNull(::decodeSchedule),
    )

    companion object {
        private const val K_ADDED = "watched_added"
        private const val K_REMOVED = "watched_removed"
        private const val K_TIME_OVER = "on_time_over"
        private const val K_BLOCKS = "blocks"
        private const val K_SCHEDULES = "schedules"

        fun encodeApp(a: AppId) = "${a.packageName}#${a.profile}"

        fun decodeApp(s: String): AppId? {
            val i = s.lastIndexOf('#')
            if (i <= 0) return null
            val profile = s.substring(i + 1).toLongOrNull() ?: return null
            return AppId(s.substring(0, i), profile)
        }

        private fun encodeTarget(t: Target): String = when (t) {
            is Target.AllExceptEssentials -> "all"
            is Target.Apps -> "apps:" + t.apps.joinToString(",", transform = ::encodeApp)
        }

        private fun decodeTarget(s: String): Target? = when {
            s == "all" -> Target.AllExceptEssentials
            s.startsWith("apps:") -> Target.Apps(s.removePrefix("apps:").split(',').mapNotNull(::decodeApp).toSet())
            else -> null
        }

        private fun encodeBlock(b: Block): String = listOf(
            b.id, encodeTarget(b.target), b.reason.name,
            b.start.wall.toEpochMilli().toString(), b.start.elapsedMs.toString(), b.start.bootId,
            b.durationMs.toString(),
        ).joinToString("\t")

        /** One line: id, name, days (1 = Monday), start, end, scope, strictness, on/off. */
        private fun encodeSchedule(saved: SavedSchedule): String {
            val s = saved.schedule
            return listOf(
                s.id, s.name.replace(Regex("[\t\n]"), " "),
                s.days.sorted().joinToString(",") { it.value.toString() },
                s.start.toString(), s.end.toString(),
                saved.scope.name, s.strictness.name, s.enabled.toString(),
            ).joinToString("\t")
        }

        private fun decodeSchedule(line: String): SavedSchedule? {
            val f = line.split('\t')
            if (f.size < 8) return null
            return runCatching {
                SavedSchedule(
                    schedule = Schedule(
                        id = f[0],
                        name = f[1],
                        days = f[2].split(',').filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
                        start = LocalTime.parse(f[3]),
                        end = LocalTime.parse(f[4]),
                        target = Target.Apps(emptySet()),
                        strictness = Strictness.valueOf(f[6]),
                        enabled = f[7].toBoolean(),
                    ),
                    scope = ScheduleScope.valueOf(f[5]),
                )
            }.getOrNull()
        }

        private fun decodeBlock(line: String): Block? {
            val f = line.split('\t')
            if (f.size < 7) return null
            return runCatching {
                Block(
                    id = f[0],
                    target = decodeTarget(f[1]) ?: return null,
                    reason = BlockReason.valueOf(f[2]),
                    start = Moment(Instant.ofEpochMilli(f[3].toLong()), f[4].toLong(), f[5]),
                    durationMs = f[6].toLong(),
                )
            }.getOrNull()
        }
    }
}
