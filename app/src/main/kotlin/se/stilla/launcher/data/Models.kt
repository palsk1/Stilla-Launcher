package se.stilla.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import se.stilla.engine.Distractions

/**
 * Identifies one launchable activity for one profile (personal or work).
 * The encoded form is what settings (favorites, renames, hidden) are keyed by.
 */
data class AppKey(val packageName: String, val className: String, val userSerial: Long) {
    fun encode(): String = "$packageName/$className#$userSerial"
}

/** An app as Android reports it, before your own settings are applied. */
data class RawApp(
    val key: AppKey,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val firstInstall: Long,
    val isWork: Boolean,
    val isSystem: Boolean,
    /** What the developer files it under on Google Play (social, game…). */
    val category: Distractions.Category = Distractions.Category.OTHER,
)

enum class ThemeChoice { BLACK, DARK_GREY, SILVER, GRAPHITE, PAPER }

/** Your own settings, as stored on the phone. */
data class PrefsState(
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val essentialsAdded: Set<String> = emptySet(),
    val essentialsRemoved: Set<String> = emptySet(),
    val theme: ThemeChoice = ThemeChoice.BLACK,
    /** Multiplies the phone's own font size. */
    val textScale: Float = 1f,
    /** The setup checklist has been finished or skipped once. */
    val setupDone: Boolean = false,
    /** Hide the status bar (and its notification icons) on Stilla's screens. */
    val hideStatusBar: Boolean = true,
    /** The thin battery line under the clock. */
    val showBattery: Boolean = true,
)
