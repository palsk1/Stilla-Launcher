package se.stilla.launcher.guard

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Knows which app is in front, using Android's usage history ("Usage access")
 * instead of an accessibility service. Banking apps such as Nordea refuse to
 * run while an accessibility service from outside the Play Store is on;
 * usage access doesn't trigger that.
 *
 * Checks a few times a second, only while the screen is on and unlocked.
 * Reads only "which app's screen came to the front", nothing else.
 */
class ForegroundPoller(
    private val context: Context,
    private val onFront: (pkg: String) -> Unit,
) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var since = 0L

    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            check()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (running || !hasUsageAccess(context)) return
        running = true
        // Look back a little so we know what's in front right now.
        since = System.currentTimeMillis() - LOOK_BACK_MS
        handler.post(poll)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(poll)
    }

    private fun check() {
        val now = System.currentTimeMillis()
        val latest = try {
            val events = usm.queryEvents(since, now)
            val e = UsageEvents.Event()
            var pkg: String? = null
            var lastTs = since
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.timeStamp > lastTs) lastTs = e.timeStamp
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val p = e.packageName ?: continue
                    if (p in IGNORED || p == currentKeyboard()) continue
                    pkg = p
                }
            }
            since = lastTs + 1
            pkg
        } catch (e: Exception) {
            Log.w("Stilla", "Usage events failed", e)
            null
        }
        if (latest != null) onFront(latest)
    }

    private fun currentKeyboard(): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    companion object {
        private const val INTERVAL_MS = 500L
        private const val LOOK_BACK_MS = 15_000L

        private val IGNORED = setOf(
            "com.android.systemui",
            "android",
            "com.samsung.android.app.aodservice",
            "com.samsung.android.app.cocktailbarservice",
        )

        fun hasUsageAccess(context: Context): Boolean {
            val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            return if (mode == AppOpsManager.MODE_DEFAULT) {
                context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                mode == AppOpsManager.MODE_ALLOWED
            }
        }

        fun canShowCards(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
