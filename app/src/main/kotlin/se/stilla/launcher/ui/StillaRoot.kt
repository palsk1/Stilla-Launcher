package se.stilla.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.stilla.launcher.AppEntry
import se.stilla.launcher.LauncherViewModel
import se.stilla.launcher.Screen
import se.stilla.launcher.ui.apps.AppListScreen
import se.stilla.launcher.ui.apps.AppMenuSheet
import se.stilla.launcher.ui.apps.RenameDialog
import se.stilla.launcher.ui.home.HomeScreen
import se.stilla.launcher.ui.settings.SettingsScreen
import se.stilla.launcher.ui.setup.SetupScreen
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens

/** Things only the Activity can do (start other apps and system screens). */
interface LauncherActions {
    fun launch(entry: AppEntry)
    fun openAppInfo(entry: AppEntry)
    fun uninstall(entry: AppEntry)
    fun openDialer()
    fun openCamera()
    fun openAlarms()
    fun openCalendar()
    fun requestDefaultHome()
    fun openOverlaySettings()
    fun openUsageAccessSettings()
    fun requestBatteryExemption()
    fun openRestrictedSettings()
    fun openNotifications()
    fun openPlayer()
    fun openNotificationAccess()
    val versionName: String
}

@Composable
fun StillaRoot(vm: LauncherViewModel, actions: LauncherActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val isDefaultHome by vm.isDefaultHome.collectAsStateWithLifecycle()
    val menuFor by vm.menuFor.collectAsStateWithLifecycle()
    val renaming by vm.renaming.collectAsStateWithLifecycle()
    val prefs by vm.prefsState.collectAsStateWithLifecycle()
    val watcherOn by vm.guard.watcherOn.collectAsStateWithLifecycle()
    val watcherEnabled by vm.watcherEnabled.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val nowPlaying by vm.media.now.collectAsStateWithLifecycle()

    // Back goes Settings → Apps → Home, and does nothing on Home.
    BackHandler(enabled = true) { vm.back() }

    Box(modifier = Modifier.fillMaxSize().background(LocalBackground.current)) {
        Crossfade(targetState = screen, animationSpec = tween(StillaDimens.FadeMs), label = "screen") { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    favorites = state.favorites,
                    isDefaultHome = isDefaultHome,
                    onOpenApps = vm::openApps,
                    onLaunch = actions::launch,
                    onLongPress = { vm.showMenu(it.key) },
                    onPhone = actions::openDialer,
                    onCamera = actions::openCamera,
                    onClock = actions::openAlarms,
                    onDate = actions::openCalendar,
                    onSetDefault = actions::requestDefaultHome,
                    watcherPaused = !watcherEnabled,
                    onFixWatcher = vm::openSetup,
                    onSwipeDown = actions::openNotifications,
                    onMoveFavorite = { entry, index -> vm.moveFavoriteTo(entry.key, index) },
                    nowPlaying = nowPlaying,
                    onOpenPlayer = actions::openPlayer,
                    onPrevious = vm.media::previous,
                    onPlayPause = vm.media::playPause,
                    onNext = vm.media::next,
                    showBattery = prefs.showBattery,
                )
                Screen.Apps -> AppListScreen(
                    state = state,
                    query = query,
                    onQuery = vm::setQuery,
                    onLaunch = actions::launch,
                    onLongPress = { vm.showMenu(it.key) },
                    onSettings = vm::openSettings,
                )
                Screen.Settings -> SettingsScreen(
                    state = state,
                    isDefaultHome = isDefaultHome,
                    versionName = actions.versionName,
                    onSetDefault = actions::requestDefaultHome,
                    onUnhide = { vm.setHidden(it.key, false) },
                    theme = prefs.theme,
                    textScale = prefs.textScale,
                    onTheme = vm::setTheme,
                    onTextScale = vm::setTextScale,
                    watcherOn = watcherOn,
                    onOpenWatcherSettings = vm::openSetup,
                    onTimeOver = vm::setOnTimeOver,
                    onOpenSetup = vm::openSetup,
                    hideStatusBar = prefs.hideStatusBar,
                    onHideStatusBar = vm::setHideStatusBar,
                    showBattery = prefs.showBattery,
                    onShowBattery = vm::setShowBattery,
                )
                Screen.Setup -> SetupScreen(
                    status = setup,
                    onHome = actions::requestDefaultHome,
                    onOverlay = actions::openOverlaySettings,
                    onUsage = actions::openUsageAccessSettings,
                    onBattery = actions::requestBatteryExemption,
                    onMedia = actions::openNotificationAccess,
                    onRestricted = actions::openRestrictedSettings,
                    onFinish = vm::finishSetup,
                )
            }
        }
    }

    // Prompt, block and time's-up cards have their own screen: guard/GuardActivity.kt.

    val menuKey = menuFor
    if (menuKey != null) {
        val entry = state.byKey[menuKey]
        if (entry == null) {
            // The app was uninstalled while its menu was open.
            LaunchedEffect(menuKey) { vm.dismissMenu() }
        } else {
            AppMenuSheet(
                entry = entry,
                favoritesFull = state.favoritesFull,
                onDismiss = vm::dismissMenu,
                onToggleFavorite = { vm.toggleFavorite(entry.key) },
                onRename = { vm.startRename(entry.key) },
                onToggleHidden = { vm.setHidden(entry.key, !entry.hidden) },
                onToggleEssential = { vm.setEssential(entry.raw.key.packageName, !entry.essential) },
                onAppInfo = { actions.openAppInfo(entry) },
                onUninstall = { actions.uninstall(entry) },
                onToggleWatched = { vm.setWatched(entry, !entry.watched) },
                onBlock = { ms -> vm.blockApp(entry, ms) },
            )
        }
    }

    val renameKey = renaming
    if (renameKey != null) {
        val entry = state.byKey[renameKey]
        if (entry == null) {
            LaunchedEffect(renameKey) { vm.dismissRename() }
        } else {
            RenameDialog(
                entry = entry,
                onSave = { vm.rename(entry.key, it) },
                onDismiss = vm::dismissRename,
            )
        }
    }
}
