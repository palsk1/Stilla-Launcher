package se.stilla.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.stilla.engine.Distractions

/**
 * The list of launchable apps, for every profile (personal and work).
 *
 * Starts from a snapshot saved on the phone, so the list draws instantly
 * after a restart, then checks with LauncherApps in the background and
 * listens for installs, updates and removals.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val snapshot = context.getSharedPreferences("stilla_app_snapshot", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val _apps = MutableStateFlow(loadSnapshot())
    val apps: StateFlow<List<RawApp>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    /** Re-reads the app list. Bursts of calls (an app update fires several) collapse into one. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(120)
            val fresh = query()
            if (fresh != _apps.value) {
                _apps.value = fresh
                saveSnapshot(fresh)
            }
        }
    }

    private fun query(): List<RawApp> {
        val self = context.packageName
        val me = Process.myUserHandle()
        val result = ArrayList<RawApp>()
        for (user in userManager.userProfiles) {
            val serial = userManager.getSerialNumberForUser(user)
            val activities = try {
                launcherApps.getActivityList(null, user)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't list apps for a profile", e)
                emptyList()
            }
            for (info in activities) {
                val cn = info.componentName
                if (cn.packageName == self) continue
                result += RawApp(
                    key = AppKey(cn.packageName, cn.className, serial),
                    component = cn,
                    user = user,
                    label = info.label?.toString()?.trim().orEmpty().ifEmpty { cn.packageName },
                    firstInstall = info.firstInstallTime,
                    isWork = user != me,
                    isSystem = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    category = categoryOf(info.applicationInfo.category),
                )
            }
        }
        return result
    }

    private fun saveSnapshot(apps: List<RawApp>) {
        val text = apps.joinToString("\n") { a ->
            listOf(
                a.component.packageName,
                a.component.className,
                a.key.userSerial.toString(),
                a.label.replace('\t', ' ').replace('\n', ' '),
                a.firstInstall.toString(),
                if (a.isWork) "1" else "0",
                if (a.isSystem) "1" else "0",
                a.category.name,
            ).joinToString("\t")
        }
        snapshot.edit().putString(K_SNAPSHOT, text).apply()
    }

    private fun loadSnapshot(): List<RawApp> {
        val text = snapshot.getString(K_SNAPSHOT, null) ?: return emptyList()
        return text.split('\n').mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 7) return@mapNotNull null
            val serial = f[2].toLongOrNull() ?: return@mapNotNull null
            val user = userManager.getUserForSerialNumber(serial) ?: return@mapNotNull null
            RawApp(
                key = AppKey(f[0], f[1], serial),
                component = ComponentName(f[0], f[1]),
                user = user,
                label = f[3],
                firstInstall = f[4].toLongOrNull() ?: 0L,
                isWork = f[5] == "1",
                isSystem = f[6] == "1",
                category = f.getOrNull(7)?.let { runCatching { Distractions.Category.valueOf(it) }.getOrNull() }
                    ?: Distractions.Category.OTHER,
            )
        }
    }

    private fun categoryOf(c: Int): Distractions.Category = when (c) {
        ApplicationInfo.CATEGORY_SOCIAL -> Distractions.Category.SOCIAL
        ApplicationInfo.CATEGORY_GAME -> Distractions.Category.GAME
        ApplicationInfo.CATEGORY_VIDEO -> Distractions.Category.VIDEO
        else -> Distractions.Category.OTHER
    }

    private companion object {
        const val TAG = "Stilla"
        const val K_SNAPSHOT = "apps_v1"
    }
}
