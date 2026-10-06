package se.stilla.launcher.guard

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import se.stilla.engine.EngineClock
import se.stilla.engine.Moment
import java.time.Instant
import java.time.ZoneId

/** The real clocks: wall time, time since boot, and which boot this is. */
class AndroidClock(context: Context) : EngineClock {
    private val bootId: String = "boot" + runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrElse {
        // No boot count: derive one from when the phone started, to the nearest minute.
        (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000
    }

    override val zone: ZoneId get() = ZoneId.systemDefault()

    override fun now(): Moment = Moment(Instant.now(), SystemClock.elapsedRealtime(), bootId)
}
