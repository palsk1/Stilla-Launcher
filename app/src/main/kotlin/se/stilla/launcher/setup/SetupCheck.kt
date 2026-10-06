package se.stilla.launcher.setup

import android.content.Context
import android.os.PowerManager
import se.stilla.launcher.guard.ForegroundPoller

/** Where each setup step stands. Read again every time Stilla comes back to the front. */
data class SetupStatus(
    val isHome: Boolean = true,
    /** "Display over other apps": lets Stilla put its card in front of a watched app. */
    val overlay: Boolean = true,
    /** "Usage access": lets Stilla see which app is in front. */
    val usage: Boolean = true,
    /** The phone won't put Stilla to sleep to save battery. */
    val batteryFree: Boolean = true,
    /** Optional "Notification access": home shows the song and its controls. */
    val media: Boolean = true,
    /** Installed from a file: Android greys out some switches until "Allow restricted settings". */
    val restricted: Boolean = false,
) {
    val requiredDone: Boolean get() = isHome && overlay && usage
}

object SetupCheck {

    fun read(context: Context, isHome: Boolean) = SetupStatus(
        isHome = isHome,
        overlay = ForegroundPoller.canShowCards(context),
        usage = ForegroundPoller.hasUsageAccess(context),
        batteryFree = batteryFree(context),
        media = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        restricted = restricted(context),
    )

    /** Reads the app-op Android uses for "restricted settings" (Android 13+). */
    private fun restricted(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return false
        val mode = runCatching {
            context.getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow(
                "android:access_restricted_settings", android.os.Process.myUid(), context.packageName,
            )
        }.getOrNull() ?: return false
        return mode != android.app.AppOpsManager.MODE_ALLOWED && mode != android.app.AppOpsManager.MODE_DEFAULT
    }

    private fun batteryFree(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}
