package se.stilla.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import se.stilla.engine.Alphabet
import se.stilla.engine.AppId
import se.stilla.engine.OnTimeOver
import se.stilla.engine.AppSearch
import se.stilla.engine.Essentials
import se.stilla.engine.SearchItem
import se.stilla.launcher.data.PrefsState
import se.stilla.launcher.data.RawApp
import se.stilla.launcher.data.RulesState
import se.stilla.launcher.data.ThemeChoice
import se.stilla.launcher.setup.SetupStatus
import java.text.Collator
import java.util.Locale

enum class Screen { Home, Apps, Settings, Setup }

/** One row in the app list: the app plus your settings for it. */
data class AppEntry(
    val key: String,
    val raw: RawApp,
    val label: String,
    val section: String,
    val hidden: Boolean,
    val favorite: Boolean,
    val essential: Boolean,
    val essentialPermanent: Boolean,
    val appId: AppId,
    /** "Ask how long first" is on. */
    val watched: Boolean,
)

data class LauncherState(
    val all: List<AppEntry> = emptyList(),
    val visible: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val recent: List<AppEntry> = emptyList(),
    val byKey: Map<String, AppEntry> = emptyMap(),
    val search: AppSearch<String> = AppSearch(emptyList()),
    val favoritesFull: Boolean = false,
    val rules: RulesState = RulesState(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as StillaApp).container
    private val repo = container.apps
    private val prefs = container.prefs
    private val ruleStore = container.rules
    val guard = container.guard
    val media = container.media
    private val self = app.packageName

    val state: StateFlow<LauncherState> =
        combine(repo.apps, prefs.state, ruleStore.state) { apps, p, r -> build(apps, p, r) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, build(repo.apps.value, prefs.state.value, ruleStore.state.value))

    /** Your settings as stored (theme, text size, …). */
    val prefsState: StateFlow<PrefsState> = prefs.state

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _watcherEnabled = MutableStateFlow(true)
    /** The app watcher is switched on in Android's settings. */
    val watcherEnabled: StateFlow<Boolean> = _watcherEnabled.asStateFlow()

    private val _setup = MutableStateFlow(SetupStatus())
    /** Where each setup step stands; refreshed whenever Stilla comes to the front. */
    val setup: StateFlow<SetupStatus> = _setup.asStateFlow()

    private val _isDefaultHome = MutableStateFlow(true)
    val isDefaultHome: StateFlow<Boolean> = _isDefaultHome.asStateFlow()

    /** Key of the app whose long-press menu is open. */
    private val _menuFor = MutableStateFlow<String?>(null)
    val menuFor: StateFlow<String?> = _menuFor.asStateFlow()

    /** Key of the app being renamed. */
    private val _renaming = MutableStateFlow<String?>(null)
    val renaming: StateFlow<String?> = _renaming.asStateFlow()

    // Navigation

    fun goHome() {
        _screen.value = Screen.Home
        _query.value = ""
        _menuFor.value = null
        _renaming.value = null
    }

    fun openApps() {
        _query.value = ""
        _screen.value = Screen.Apps
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun openSetup() {
        _menuFor.value = null
        _screen.value = Screen.Setup
    }

    /** "All set" or "Later": the checklist won't open by itself again. */
    fun finishSetup() {
        prefs.setSetupDone()
        goHome()
    }

    fun setSetupStatus(status: SetupStatus) {
        _setup.value = status
    }

    /** Returns false when already home, where Back does nothing. */
    fun back(): Boolean = when (_screen.value) {
        Screen.Home -> false
        Screen.Apps -> { goHome(); true }
        Screen.Settings -> { _screen.value = Screen.Apps; true }
        Screen.Setup -> { finishSetup(); true }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setDefaultHome(isDefault: Boolean) {
        _isDefaultHome.value = isDefault
    }

    fun setWatcherEnabled(enabled: Boolean) {
        _watcherEnabled.value = enabled
    }

    fun refreshApps() = repo.refresh()

    // Long-press menu

    fun showMenu(key: String) { _menuFor.value = key }
    fun dismissMenu() { _menuFor.value = null }
    fun startRename(key: String) { _menuFor.value = null; _renaming.value = key }
    fun dismissRename() { _renaming.value = null }

    fun toggleFavorite(key: String) = prefs.toggleFavorite(key)
    fun moveFavoriteTo(key: String, index: Int) = prefs.moveFavoriteTo(key, index)
    fun setHidden(key: String, hidden: Boolean) = prefs.setHidden(key, hidden)
    fun rename(key: String, label: String?) { prefs.rename(key, label); _renaming.value = null }
    fun setEssential(packageName: String, essential: Boolean) = prefs.setEssential(packageName, essential)
    fun setTheme(theme: ThemeChoice) = prefs.setTheme(theme)
    fun setWatched(entry: AppEntry, watched: Boolean) = ruleStore.setWatched(entry.appId, watched)
    fun setOnTimeOver(mode: OnTimeOver) = ruleStore.setOnTimeOver(mode)
    fun blockApp(entry: AppEntry, durationMs: Long) = guard.blockApp(entry.appId, durationMs)
    fun setTextScale(scale: Float) = prefs.setTextScale(scale)
    fun setHideStatusBar(hide: Boolean) = prefs.setHideStatusBar(hide)

    // Building the list

    private fun build(apps: List<RawApp>, p: PrefsState, r: RulesState): LauncherState {
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.SECONDARY }
        val watchedSet = r.watchedAmong(apps)
        val entries = apps.map { raw ->
            val key = raw.key.encode()
            val label = p.renames[key]?.takeIf { it.isNotBlank() } ?: raw.label
            val pkg = raw.key.packageName
            val appId = AppId(pkg, raw.key.userSerial)
            AppEntry(
                key = key,
                raw = raw,
                label = label,
                section = Alphabet.sectionOf(label),
                hidden = key in p.hidden,
                favorite = key in p.favorites,
                essential = Essentials.isEssential(pkg, p.essentialsAdded, p.essentialsRemoved, self),
                essentialPermanent = !Essentials.canRemove(pkg, self),
                appId = appId,
                watched = appId in watchedSet,
            )
        }.sortedWith(
            compareBy<AppEntry> { Alphabet.orderOf(it.section) }
                .thenComparator { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.raw.isWork },
        )
        val byKey = entries.associateBy { it.key }
        val visible = entries.filter { !it.hidden }
        val now = System.currentTimeMillis()
        val recent = visible
            .filter { it.raw.firstInstall > 0 && now - it.raw.firstInstall in 0..RECENT_MS }
            .sortedByDescending { it.raw.firstInstall }
            .take(RECENT_COUNT)
        val favorites = p.favorites.mapNotNull { byKey[it] }
        return LauncherState(
            all = entries,
            visible = visible,
            favorites = favorites,
            recent = recent,
            byKey = byKey,
            search = AppSearch(entries.map { SearchItem(it.key, it.label, it.hidden) }),
            favoritesFull = p.favorites.size >= se.stilla.launcher.data.StillaPrefs.MAX_FAVORITES,
            rules = r,
        )
    }

    private companion object {
        const val RECENT_MS = 3L * 24 * 60 * 60 * 1000
        const val RECENT_COUNT = 3
    }
}
