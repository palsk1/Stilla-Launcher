package se.stilla.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Favorites, hidden apps, renames and essentials.
 *
 * Plain SharedPreferences for now: tiny, synchronous to read (so the first
 * frame already has your favorites) and written in the background. Moves to
 * Room in Phase 2, when rules, blocks and sessions arrive.
 */
class StillaPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("stilla_prefs", Context.MODE_PRIVATE)
    private val renamesSp: SharedPreferences =
        context.getSharedPreferences("stilla_renames", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<PrefsState> = _state.asStateFlow()

    fun toggleFavorite(key: String) = update { s ->
        when {
            key in s.favorites -> s.copy(favorites = s.favorites - key)
            s.favorites.size >= MAX_FAVORITES -> s
            else -> s.copy(favorites = s.favorites + key)
        }
    }

    /** Moves a favorite up (-1) or down (+1) on the home screen. */
    fun moveFavorite(key: String, delta: Int) = update { s ->
        val i = s.favorites.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in s.favorites.indices) s
        else s.copy(favorites = s.favorites.toMutableList().apply { add(j, removeAt(i)) })
    }

    /** Drag and drop on home: puts a favorite at [index]. */
    fun moveFavoriteTo(key: String, index: Int) = update { s ->
        val i = s.favorites.indexOf(key)
        if (i < 0) s
        else s.copy(favorites = s.favorites.toMutableList().apply { add(index.coerceIn(0, size - 1), removeAt(i)) })
    }

    fun setHidden(key: String, hidden: Boolean) = update { s ->
        if (hidden) s.copy(hidden = s.hidden + key, favorites = s.favorites - key)
        else s.copy(hidden = s.hidden - key)
    }

    /** Pass null or blank to go back to the app's own name. */
    fun rename(key: String, label: String?) = update { s ->
        val clean = label?.trim().orEmpty()
        if (clean.isEmpty()) s.copy(renames = s.renames - key)
        else s.copy(renames = s.renames + (key to clean))
    }

    fun setEssential(packageName: String, essential: Boolean) = update { s ->
        if (essential) s.copy(
            essentialsAdded = s.essentialsAdded + packageName,
            essentialsRemoved = s.essentialsRemoved - packageName,
        ) else s.copy(
            essentialsAdded = s.essentialsAdded - packageName,
            essentialsRemoved = s.essentialsRemoved + packageName,
        )
    }

    fun setTheme(theme: ThemeChoice) = update { it.copy(theme = theme) }

    fun setTextScale(scale: Float) = update { it.copy(textScale = scale.coerceIn(0.8f, 1.4f)) }

    fun setHideStatusBar(hide: Boolean) = update { it.copy(hideStatusBar = hide) }

    fun setShowBattery(show: Boolean) = update { it.copy(showBattery = show) }

    fun setSetupDone() = update { it.copy(setupDone = true) }

    fun setFoldersOn(on: Boolean) = update { it.copy(foldersOn = on) }

    fun setShowTips(show: Boolean) = update { it.copy(showTips = show) }

    /** A new folder, with [firstApp] in it. Making your first folder also turns folders on. */
    fun createFolder(name: String, firstApp: String?) = update { s ->
        val clean = cleanName(name)
        if (clean.isEmpty()) return@update s
        val folder = Folder(id = "f" + System.currentTimeMillis(), name = clean, apps = listOfNotNull(firstApp))
        s.copy(folders = s.folders + folder, foldersOn = s.foldersOn || s.folders.isEmpty())
    }

    /** Puts the app in the folder, or takes it out if it's already there. */
    fun toggleInFolder(folderId: String, appKey: String) = updateFolder(folderId) { f ->
        if (appKey in f.apps) f.copy(apps = f.apps - appKey) else f.copy(apps = f.apps + appKey)
    }

    fun renameFolder(folderId: String, name: String) = updateFolder(folderId) { f ->
        cleanName(name).takeIf { it.isNotEmpty() }?.let { f.copy(name = it) } ?: f
    }

    /** Only the folder goes; its apps stay in the app list. */
    fun deleteFolder(folderId: String) = update { s -> s.copy(folders = s.folders.filterNot { it.id == folderId }) }

    private fun updateFolder(folderId: String, change: (Folder) -> Folder) = update { s ->
        s.copy(folders = s.folders.map { if (it.id == folderId) change(it) else it })
    }

    private fun cleanName(name: String) = name.replace(Regex("[\\t\\n]"), " ").trim().take(MAX_FOLDER_NAME)

    @Synchronized
    private fun update(change: (PrefsState) -> PrefsState) {
        val old = _state.value
        val new = change(old)
        if (new == old) return
        write(new)
        _state.value = new
    }

    private fun read(): PrefsState = PrefsState(
        favorites = sp.getString(K_FAVORITES, null)
            ?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
        hidden = sp.getStringSet(K_HIDDEN, null)?.toSet().orEmpty(),
        renames = renamesSp.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
        essentialsAdded = sp.getStringSet(K_ESS_ADDED, null)?.toSet().orEmpty(),
        essentialsRemoved = sp.getStringSet(K_ESS_REMOVED, null)?.toSet().orEmpty(),
        theme = runCatching { ThemeChoice.valueOf(sp.getString(K_THEME, null) ?: "") }.getOrDefault(ThemeChoice.BLACK),
        textScale = sp.getFloat(K_TEXT_SCALE, 1f),
        setupDone = sp.getBoolean(K_SETUP_DONE, false),
        hideStatusBar = sp.getBoolean(K_HIDE_STATUS_BAR, true),
        showBattery = sp.getBoolean(K_SHOW_BATTERY, true),
        folders = sp.getString(K_FOLDERS, null).orEmpty().split('\n').mapNotNull(::decodeFolder),
        foldersOn = sp.getBoolean(K_FOLDERS_ON, false),
        showTips = sp.getBoolean(K_SHOW_TIPS, true),
    )

    private fun write(s: PrefsState) {
        sp.edit()
            .putString(K_FAVORITES, s.favorites.joinToString("\n"))
            .putStringSet(K_HIDDEN, s.hidden)
            .putStringSet(K_ESS_ADDED, s.essentialsAdded)
            .putStringSet(K_ESS_REMOVED, s.essentialsRemoved)
            .putString(K_THEME, s.theme.name)
            .putFloat(K_TEXT_SCALE, s.textScale)
            .putBoolean(K_SETUP_DONE, s.setupDone)
            .putBoolean(K_HIDE_STATUS_BAR, s.hideStatusBar)
            .putBoolean(K_SHOW_BATTERY, s.showBattery)
            .putString(K_FOLDERS, s.folders.joinToString("\n", transform = ::encodeFolder))
            .putBoolean(K_FOLDERS_ON, s.foldersOn)
            .putBoolean(K_SHOW_TIPS, s.showTips)
            .apply()
        val e = renamesSp.edit().clear()
        s.renames.forEach { (k, v) -> e.putString(k, v) }
        e.apply()
    }

    companion object {
        const val MAX_FAVORITES = 7
        private const val K_FAVORITES = "favorites"
        private const val K_HIDDEN = "hidden"
        private const val K_ESS_ADDED = "essentials_added"
        private const val K_ESS_REMOVED = "essentials_removed"
        private const val K_THEME = "theme"
        private const val K_TEXT_SCALE = "text_scale"
        private const val K_SETUP_DONE = "setup_done"
        private const val K_HIDE_STATUS_BAR = "hide_status_bar"
        private const val K_SHOW_BATTERY = "show_battery"
        private const val K_FOLDERS = "folders"
        private const val K_FOLDERS_ON = "folders_on"
        private const val K_SHOW_TIPS = "show_tips"
        const val MAX_FOLDER_NAME = 24

        /** One line per folder: id, name, then its app keys, separated by tabs. */
        private fun encodeFolder(f: Folder): String = (listOf(f.id, f.name) + f.apps).joinToString("\t")

        private fun decodeFolder(line: String): Folder? {
            val f = line.split('\t')
            if (f.size < 2 || f[0].isEmpty()) return null
            return Folder(id = f[0], name = f[1], apps = f.drop(2).filter { it.isNotEmpty() })
        }
    }
}
