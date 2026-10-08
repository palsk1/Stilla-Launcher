package se.stilla.launcher

import android.app.role.RoleManager
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.stilla.launcher.media.MediaListener
import se.stilla.launcher.setup.SetupCheck
import se.stilla.launcher.ui.LauncherActions
import se.stilla.launcher.ui.StillaRoot
import se.stilla.launcher.ui.theme.StillaTheme

class MainActivity : ComponentActivity(), LauncherActions {

    private val vm: LauncherViewModel by viewModels()
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    private var roleRequestedAt = 0L
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val isDefault = isDefaultHome()
        vm.setDefaultHome(isDefault)
        // Android stops showing the dialog after it has been declined twice and
        // answers at once; then the only way is the system's home-app setting.
        if (!isDefault && SystemClock.elapsedRealtime() - roleRequestedAt < 600) {
            safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by vm.prefsState.collectAsStateWithLifecycle()
            LaunchedEffect(prefs.hideStatusBar) { applyStatusBar(prefs.hideStatusBar) }
            StillaTheme(theme = prefs.theme, textScale = prefs.textScale) {
                StillaRoot(vm = vm, actions = this)
            }
        }
        if (savedInstanceState == null) {
            // First run, or the watcher was just switched on from the checklist.
            if (!vm.prefsState.value.setupDone || intent?.getBooleanExtra(EXTRA_SETUP, false) == true) vm.openSetup()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.setDefaultHome(isDefaultHome())
        // Stilla is in front (helps when the app watcher is off).
        vm.guard.onAppInFront(packageName)
        vm.guard.refreshHome()
        vm.guard.openPendingCard()
        applyStatusBar(vm.prefsState.value.hideStatusBar)
        vm.guard.stopSetupWatch()
        vm.guard.refreshWatcher()
        vm.setWatcherEnabled(vm.guard.isWatcherReady())
        vm.setSetupStatus(SetupCheck.read(this, isHome = isDefaultHome()))
        vm.media.start()
    }

    /** Pressing Home while Stilla is already in front brings you back to the home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SETUP, false)) {
            vm.openSetup()
            return
        }
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.goHome()
        }
    }

    /**
     * Leaving Stilla (to open an app) resets it, so you always come back to home.
     * The setup checklist stays put: you leave it for Android's settings and come back.
     */
    override fun onStop() {
        super.onStop()
        if (vm.screen.value != Screen.Setup) vm.goHome()
    }

    /**
     * No status bar on Stilla's screens, so notification icons stay out of
     * sight until you pull down. A swipe from the top edge still shows it briefly.
     */
    private fun applyStatusBar(hide: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hide) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBar(vm.prefsState.value.hideStatusBar)
    }

    // LauncherActions

    override val versionName: String
        get() = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"

    override fun launch(entry: AppEntry) {
        // Watched, blocked or scheduled apps get the prompt or block card instead.
        if (!vm.guard.requestOpen(entry.appId, entry.label)) return
        try {
            launcherApps.startMainActivity(entry.raw.component, entry.raw.user, null, null)
            vm.guard.opened(entry.appId)
        } catch (e: Exception) {
            // Uninstalled, disabled or a paused work profile.
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
            vm.refreshApps()
        }
    }

    override fun openAppInfo(entry: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(entry.raw.component, entry.raw.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    override fun uninstall(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.raw.key.packageName, null))
            .putExtra(EXTRA_USER, entry.raw.user)
        safeStart(intent)
    }

    override fun openDialer() = safeStart(Intent(Intent.ACTION_DIAL))

    override fun openCamera() = safeStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    /**
     * Tap the clock: the alarms. Samsung's Clock doesn't answer "show alarms",
     * so fall back to opening a known clock app, then any app named like one.
     */
    override fun openAlarms() {
        if (tryStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))) return
        val clock = CLOCK_PACKAGES.firstNotNullOfOrNull(packageManager::getLaunchIntentForPackage)
            ?: findClockApp()
        if (clock != null) safeStart(clock) else Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }

    /** Any launchable app whose package name says "clock", for phones we don't know. */
    private fun findClockApp(): Intent? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pkg = packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it.contains("clock", ignoreCase = true) }
            ?: return null
        return packageManager.getLaunchIntentForPackage(pkg)
    }

    override fun openCalendar() =
        safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))

    /** "Display over other apps", on Stilla's own page when the phone allows it. */
    override fun openOverlaySettings() {
        vm.guard.returnToSetupWhenGranted()
        val direct = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", packageName, null))
        if (!tryStart(direct)) safeStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    /** "Usage access", on Stilla's own page when the phone allows it. */
    override fun openUsageAccessSettings() {
        vm.guard.returnToSetupWhenGranted()
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", packageName, null))
        if (!tryStart(direct)) safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    /** "Notification access" for the music controls, on Stilla's own switch when the phone allows it. */
    override fun openNotificationAccess() {
        vm.guard.returnToSetupWhenGranted()
        val listener = ComponentName(this, MediaListener::class.java).flattenToString()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val direct = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener)
            if (tryStart(direct)) return
        }
        safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** Tap the song on home: the player's own screen. */
    override fun openPlayer() {
        val session = vm.media.openPlayerIntent()
        if (session != null) {
            // Android 14+: we're in front, so we vouch for the player opening its screen.
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
            if (runCatching { session.send(this, 0, null, null, null, null, options.toBundle()) }.isSuccess) return
        }
        val pkg = vm.media.now.value?.packageName ?: return
        packageManager.getLaunchIntentForPackage(pkg)?.let(::safeStart)
    }

    /** Stilla's App info, where Battery → Unrestricted lives (no special permission needed). */
    override fun requestBatteryExemption() =
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    /** Same page: ⋮ → "Allow restricted settings" for apps installed from a file. */
    override fun openRestrictedSettings() = requestBatteryExemption()

    /**
     * Swipe down on home: the notification shade, as if you pulled the status bar.
     * If the phone won't allow that, the status bar shows for a few seconds instead.
     */
    @android.annotation.SuppressLint("WrongConstant")
    override fun openNotifications() {
        val ok = runCatching {
            val sbm = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }.isSuccess
        if (!ok) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.statusBars())
            window.decorView.postDelayed({ applyStatusBar(vm.prefsState.value.hideStatusBar) }, 4_000)
        }
    }

    override fun requestDefaultHome() {
        val rm = roleManager
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleRequestedAt = SystemClock.elapsedRealtime()
            roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    private fun isDefaultHome(): Boolean {
        val rm = roleManager ?: return true
        return !rm.isRoleAvailable(RoleManager.ROLE_HOME) || rm.isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Clock apps by maker, Samsung first. */
        private val CLOCK_PACKAGES = listOf(
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.android.alarmclock",
            "com.oneplus.deskclock",
        )

        /** Open the setup checklist (sent by the watcher right after it is switched on). */
        const val EXTRA_SETUP = "se.stilla.launcher.SETUP"
        private const val EXTRA_USER = "android.intent.extra.USER"
    }
}
