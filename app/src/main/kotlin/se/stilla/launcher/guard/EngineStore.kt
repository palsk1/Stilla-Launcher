package se.stilla.launcher.guard

import android.content.Context
import se.stilla.engine.AppId
import se.stilla.engine.DailyUsage
import se.stilla.engine.DecisionEngine
import se.stilla.engine.Moment
import se.stilla.engine.Session
import se.stilla.launcher.data.RuleStore
import java.time.Instant
import java.time.LocalDate

/**
 * Keeps open sessions and today's counts on the phone, so Android or Samsung
 * stopping Stilla mid-session doesn't hand out unlimited time or reset the
 * doubling extension wait. Plain text in app-private storage.
 */
class EngineStore(context: Context) {
    private val sp = context.getSharedPreferences("stilla_engine", Context.MODE_PRIVATE)

    fun save(s: DecisionEngine.Snapshot) {
        sp.edit()
            .putString(K_SESSIONS, s.sessions.joinToString("\n", transform = ::encodeSession))
            .putString(K_USAGE, s.usage.entries.joinToString("\n") { (k, u) ->
                listOf(k.first.toString(), RuleStore.encodeApp(k.second), u.foregroundMs, u.opens, u.wins, u.extensions).joinToString("\t")
            })
            .putString(K_TIME_UP, s.lastTimeUp.entries.joinToString("\n") { (app, m) -> RuleStore.encodeApp(app) + "\t" + enc(m) })
            .apply()
    }

    fun load(): DecisionEngine.Snapshot? = runCatching {
        val sessions = lines(K_SESSIONS).mapNotNull(::decodeSession)
        val usage = lines(K_USAGE).mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 6) return@mapNotNull null
            val app = RuleStore.decodeApp(f[1]) ?: return@mapNotNull null
            (LocalDate.parse(f[0]) to app) to DailyUsage(f[2].toLong(), f[3].toInt(), f[4].toInt(), f[5].toInt())
        }.toMap()
        val timeUp = lines(K_TIME_UP).mapNotNull { line ->
            val f = line.split('\t')
            val app = f.getOrNull(0)?.let(RuleStore::decodeApp) ?: return@mapNotNull null
            val m = f.getOrNull(1)?.let(::dec) ?: return@mapNotNull null
            app to m
        }.toMap()
        DecisionEngine.Snapshot(sessions, usage, timeUp)
    }.getOrNull()

    private fun lines(key: String) = sp.getString(key, null).orEmpty().split('\n').filter { it.isNotBlank() }

    private fun enc(m: Moment?): String = m?.let { "${it.wall.toEpochMilli()}|${it.elapsedMs}|${it.bootId}" } ?: "-"

    private fun dec(s: String): Moment? {
        if (s == "-") return null
        val f = s.split('|')
        if (f.size < 3) return null
        return Moment(Instant.ofEpochMilli(f[0].toLong()), f[1].toLong(), f[2])
    }

    private fun encodeSession(s: Session): String = listOf(
        RuleStore.encodeApp(s.app), enc(s.started), s.allowedMs, s.chosenMinutes, s.usedMs,
        enc(s.inFrontSince), enc(s.leftAt), s.extensions, if (s.timeUpSent) 1 else 0, enc(s.lastRemind),
    ).joinToString("\t")

    private fun decodeSession(line: String): Session? {
        val f = line.split('\t')
        if (f.size < 10) return null
        return Session(
            app = RuleStore.decodeApp(f[0]) ?: return null,
            started = dec(f[1]) ?: return null,
            allowedMs = f[2].toLong(),
            chosenMinutes = f[3].toInt(),
            usedMs = f[4].toLong(),
            inFrontSince = dec(f[5]),
            leftAt = dec(f[6]),
            extensions = f[7].toInt(),
            timeUpSent = f[8] == "1",
            lastRemind = dec(f[9]),
        )
    }

    private companion object {
        const val K_SESSIONS = "sessions"
        const val K_USAGE = "usage"
        const val K_TIME_UP = "time_up"
    }
}
