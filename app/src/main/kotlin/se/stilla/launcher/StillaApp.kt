package se.stilla.launcher

import android.app.Application
import se.stilla.launcher.data.AppRepository
import se.stilla.launcher.data.RuleStore
import se.stilla.launcher.data.StillaPrefs
import se.stilla.launcher.guard.Guard
import se.stilla.launcher.media.MediaWatcher

/** Creates the few shared objects once, at app start. No DI framework on purpose. */
class StillaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val prefs = StillaPrefs(app)
    val apps = AppRepository(app)
    val rules = RuleStore(app)
    val guard = Guard(app, prefs, rules, apps)
    val media = MediaWatcher(app)
}
