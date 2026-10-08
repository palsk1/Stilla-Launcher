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
import se.stilla.engine.Commitment
import se.stilla.engine.Essentials
import se.stilla.engine.Makers
import se.stilla.engine.Schedule
import se.stilla.engine.Strictness
import se.stilla.engine.SearchItem
import se.stilla.launcher.data.PrefsState
import se.stilla.launcher.data.RawApp
import se.stilla.launcher.data.RulesState
import se.stilla.launcher.data.SavedSchedule
import se.stilla.launcher.data.ScheduleScope
import se.stilla.launcher.data.ThemeChoice
import se.stilla.launcher.setup.SetupStatus
import java.text.Collator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

enum class Screen { Home, Apps, Settings, Setup, Schedules, ScheduleEdit, Folders, Folder }

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
    /** Who made it, shown only when another app has the same name ("· Google"). */
    val maker: String? = null,
)

/** A folder with the apps in it that are still installed. */
data class FolderView(val folder: se.stilla.launcher.data.Folder, val apps: List<AppEntry>) {
    val id: String get() = folder.id
    val name: String get() = folder.name
}

data class LauncherState(
    val all: List<AppEntry> = emptyList(),
    val visible: List<AppEntry> = emptyList(),
    val favorites: List<AppEntry> = emptyList(),
    val recent: List<AppEntry> = emptyList(),
    val byKey: Map<String, AppEntry> = emptyMap(),
    val search: AppSearch<String> = AppSearch(emptyList()),
    val favoritesFull: Boolean = false,
    val rules: RulesState = RulesState(),
    val folders: List<FolderView> = emptyList(),
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

    /** The schedule open for editing (a new one has an id not saved yet). */
    private val _editing = MutableStateFlow<SavedSchedule?>(null)
    val editing: StateFlow<SavedSchedule?> = _editing.asStateFlow()

    /** The folder open on screen. */
    private val _openFolder = MutableStateFlow<String?>(null)
    val openFolder: StateFlow<String?> = _openFolder.asStateFlow()

    /** Key of the app whose "Add to folder" list is open. */
    private val _folderPickerFor = MutableStateFlow<String?>(null)
    val folderPickerFor: StateFlow<String?> = _folderPickerFor.asStateFlow()

    /** The folder whose rename/delete card is open. */
    private val _editingFolder = MutableStateFlow<String?>(null)
    val editingFolder: StateFlow<String?> = _editingFolder.asStateFlow()

    /** Key of the app being renamed. */
    private val _renaming = MutableStateFlow<String?>(null)
    val renaming: StateFlow<String?> = _renaming.asStateFlow()

    // Navigation

    fun goHome() {
        _screen.value = Screen.Home
        _query.value = ""
        _menuFor.value = null
        _renaming.value = null
        _openFolder.value = null
        _folderPickerFor.value = null
        _editingFolder.value = null
    }

    fun openFolders() {
        _openFolder.value = null
        _screen.value = Screen.Folders
    }

    fun openFolder(id: String) {
        _openFolder.value = id
        _screen.value = Screen.Folder
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

    fun openSchedules() {
        _editing.value = null
        _screen.value = Screen.Schedules
    }

    /** A new schedule starts as "Night, 22–07 every day, time-wasters, fully off". */
    fun newSchedule() {
        val first = ruleStore.state.value.schedules.isEmpty()
        _editing.value = SavedSchedule(
            schedule = Schedule(
                id = "s" + System.currentTimeMillis(),
                name = if (first) getApplication<StillaApp>().getString(R.string.schedule_default_name) else "",
                days = DayOfWeek.entries.toSet(),
                start = LocalTime.of(22, 0),
                end = LocalTime.of(7, 0),
                target = se.stilla.engine.Target.Apps(emptySet()),
                strictness = Strictness.HARD,
            ),
            scope = ScheduleScope.WATCHED,
        )
        _screen.value = Screen.ScheduleEdit
    }

    fun editSchedule(id: String) {
        _editing.value = ruleStore.state.value.schedules.firstOrNull { it.id == id } ?: return
        _screen.value = Screen.ScheduleEdit
    }

    /**
     * When the schedule is running right now, it can only be made stricter:
     * turning it off, shortening it or covering fewer apps waits until it ends.
     * Returns that end when the change is refused, or null when it was saved.
     */
    fun saveSchedule(new: SavedSchedule): Instant? {
        val old = ruleStore.state.value.schedules.firstOrNull { it.id == new.id }
        runningUntilIfLoosening(old, new)?.let { return it }
        val name = new.schedule.name.trim().ifBlank { getApplication<StillaApp>().getString(R.string.schedule_unnamed) }
        ruleStore.saveSchedule(new.copy(schedule = new.schedule.copy(name = name)))
        openSchedules()
        return null
    }

    /** Same rule as [saveSchedule]: a running schedule can't be deleted until it ends. */
    fun deleteSchedule(id: String): Instant? {
        val old = ruleStore.state.value.schedules.firstOrNull { it.id == id } ?: return null
        runningUntilIfLoosening(old, null)?.let { return it }
        ruleStore.deleteSchedule(id)
        openSchedules()
        return null
    }

    /** When [old] is active now, when it ends. */
    fun runningUntil(old: SavedSchedule): Instant? =
        old.schedule.activeWindow(Instant.now(), ZoneId.systemDefault())?.endInstant

    private fun runningUntilIfLoosening(old: SavedSchedule?, new: SavedSchedule?): Instant? {
        old ?: return null
        val until = runningUntil(old) ?: return null
        val all = state.value.all.map { it.appId }.toSet()
        val watched = state.value.all.filter { it.watched }.map { it.appId }.toSet()
        val loosening = Commitment.isLoosening(old.resolved(watched, all), new?.resolved(watched, all))
        return if (loosening) until else null
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
        Screen.Schedules -> { _screen.value = Screen.Settings; true }
        Screen.ScheduleEdit -> { openSchedules(); true }
        Screen.Folders -> { goHome(); true }
        Screen.Folder -> { openFolders(); true }
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
    fun setShowBattery(show: Boolean) = prefs.setShowBattery(show)

    // Folders

    fun setFoldersOn(on: Boolean) = prefs.setFoldersOn(on)
    fun setShowTips(show: Boolean) = prefs.setShowTips(show)
    fun showFolderPicker(key: String) { _menuFor.value = null; _folderPickerFor.value = key }
    fun dismissFolderPicker() { _folderPickerFor.value = null }
    fun createFolder(name: String, appKey: String?) = prefs.createFolder(name, appKey)
    fun toggleInFolder(folderId: String, appKey: String) = prefs.toggleInFolder(folderId, appKey)
    fun editFolder(id: String) { _editingFolder.value = id }
    fun dismissEditFolder() { _editingFolder.value = null }
    fun renameFolder(id: String, name: String) { prefs.renameFolder(id, name); _editingFolder.value = null }

    fun deleteFolder(id: String) {
        prefs.deleteFolder(id)
        _editingFolder.value = null
        if (_openFolder.value == id) openFolders()
    }

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
        }.let { list ->
            // Two apps with the same name (two Authenticators): add who made each.
            val clashes = list.groupBy { it.label.lowercase() to it.raw.isWork }.filterValues { it.size > 1 }.values.flatten()
                .map { it.key }.toSet()
            list.map { if (it.key in clashes) it.copy(maker = Makers.of(it.raw.key.packageName)) else it }
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
        val folders = p.folders.map { f -> FolderView(f, f.apps.mapNotNull { byKey[it] }) }
        return LauncherState(
            all = entries,
            visible = visible,
            favorites = favorites,
            recent = recent,
            byKey = byKey,
            search = AppSearch(entries.map { SearchItem(it.key, it.label, it.hidden) }),
            favoritesFull = p.favorites.size >= se.stilla.launcher.data.StillaPrefs.MAX_FAVORITES,
            rules = r,
            folders = folders,
        )
    }

    private companion object {
        const val RECENT_MS = 3L * 24 * 60 * 60 * 1000
        const val RECENT_COUNT = 3
    }
}
