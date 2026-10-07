package se.stilla.launcher.guard

import android.app.KeyguardManager
import android.media.AudioManager
import android.os.SystemClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import se.stilla.engine.AppId
import se.stilla.engine.BlockReason
import se.stilla.engine.DecisionEngine
import se.stilla.engine.EngineEvent
import se.stilla.engine.Essentials
import se.stilla.engine.OnTimeOver
import se.stilla.engine.Target
import se.stilla.engine.Verdict
import se.stilla.engine.WatchRule
import se.stilla.launcher.R
import se.stilla.launcher.data.AppRepository
import se.stilla.launcher.data.RuleStore
import se.stilla.launcher.data.StillaPrefs

/** A full-screen card Stilla shows on top of home. */
sealed interface Overlay {
    val app: AppId
    val label: String

    data class Prompt(override val app: AppId, override val label: String, val verdict: Verdict.Prompt) : Overlay
    data class Blocked(override val app: AppId, override val label: String, val verdict: Verdict.Blocked) : Overlay
    data class TimeUp(override val app: AppId, override val label: String, val event: EngineEvent.TimeUp) : Overlay
}

/**
 * Hosts the decision engine on the main thread and connects it to Android:
 * the usage-access poller reports which app is in front, the screen turning
 * off pauses everything, and a timer fires when a session runs out.
 *
 * Stilla's own app list asks the guard before opening anything, so the
 * prompt works even without the watcher; the watcher adds apps opened from
 * notifications, Recents or other apps.
 */
class Guard(
    private val context: Context,
    private val prefs: StillaPrefs,
    private val store: RuleStore,
    private val repo: AppRepository,
) {
    private val self = context.packageName
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val mySerial = context.getSystemService(UserManager::class.java)
        .getSerialNumberForUser(Process.myUserHandle())
    private val selfId = AppId(self, mySerial)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val engine = DecisionEngine(
        AndroidClock(context),
        DecisionEngine.Config(isEssential = ::isEssential),
    )

    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay.asStateFlow()

    private val _winsToday = MutableStateFlow(engine.winsToday())
    /** Prompts you backed out of today. */
    val winsToday: StateFlow<Int> = _winsToday.asStateFlow()

    private val _watcherOn = MutableStateFlow(false)
    val watcherOn: StateFlow<Boolean> = _watcherOn.asStateFlow()

    private val poller = ForegroundPoller(context) { pkg -> onAppInFront(pkg) }
    private var lastFrontPkg: String? = null
    private var screenOff = false
    private var overlaySetAt = 0L
    private val engineStore = EngineStore(context)
    private val audio = context.getSystemService(AudioManager::class.java)

    private val tickRunnable = Runnable { runTick() }

    init {
        engineStore.load()?.let(engine::restore)
        _winsToday.value = engine.winsToday()
        scope.launch {
            combine(store.state, repo.apps) { s, apps -> s to s.watchedAmong(apps) }.collect { (s, watched) ->
                engine.setRules(watched.map { WatchRule(it, onTimeOver = s.onTimeOver) })
                engine.setBlocks(s.blocks)
                scheduleTick()
            }
        }
        val screen = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    // Time with the screen off doesn't count, and doesn't end a session.
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOff = true
                        poller.stop()
                        engine.suspend()
                        handler.removeCallbacks(tickRunnable)
                        save()
                    }
                    Intent.ACTION_USER_PRESENT -> wake()
                    Intent.ACTION_SCREEN_ON -> {
                        // No lock screen at all: there won't be a USER_PRESENT.
                        val km = context.getSystemService(KeyguardManager::class.java)
                        if (km?.isKeyguardLocked == false) wake()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshWatcher()
    }

    fun appId(packageName: String, userSerial: Long = mySerial) = AppId(packageName, userSerial)

    private fun isEssential(app: AppId): Boolean {
        val p = prefs.state.value
        return Essentials.isEssential(app.packageName, p.essentialsAdded, p.essentialsRemoved, self)
    }

    // ---- The watcher ----

    /**
     * Re-reads whether the watcher can work: Usage access (to know what's in
     * front) and "Display over other apps" (to put a card in front of it).
     * Called whenever Stilla comes back to the front.
     */
    fun refreshWatcher() {
        val ready = isWatcherReady()
        _watcherOn.value = ready
        if (ForegroundPoller.hasUsageAccess(context) && !screenOff) poller.start() else poller.stop()
    }

    fun isWatcherReady(): Boolean =
        ForegroundPoller.hasUsageAccess(context) && ForegroundPoller.canShowCards(context)

    private var setupWatchUntil = 0L
    private var setupStartState = 0

    private val setupWatch = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() > setupWatchUntil) return
            if (grantState() != setupStartState && ForegroundPoller.canShowCards(context)) {
                // Just switched on in Android's settings: back to the checklist.
                runCatching {
                    context.startActivity(
                        Intent(context, se.stilla.launcher.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(se.stilla.launcher.MainActivity.EXTRA_SETUP, true),
                    )
                }
                return
            }
            handler.postDelayed(this, 400)
        }
    }

    private fun grantState(): Int =
        (if (ForegroundPoller.hasUsageAccess(context)) 1 else 0) +
            (if (ForegroundPoller.canShowCards(context)) 2 else 0) +
            (if (androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(self)) 4 else 0)

    /** The checklist sent you to a settings page: come back as soon as the switch is on. */
    fun returnToSetupWhenGranted() {
        setupStartState = grantState()
        setupWatchUntil = SystemClock.elapsedRealtime() + 5 * 60_000L
        handler.removeCallbacks(setupWatch)
        handler.postDelayed(setupWatch, 400)
    }

    /** Stilla is in front again: stop waiting for a switch. */
    fun stopSetupWatch() {
        handler.removeCallbacks(setupWatch)
    }

    /**
     * The watcher (or Stilla itself) reports which app is now in front.
     * [isActivity] is false for pop-ups and overlay windows (share sheet, Edge
     * panel, in-call screen…): they pause the clock but never cause a bounce.
     */
    fun onAppInFront(pkg: String, isActivity: Boolean = true) {
        if (pkg == lastFrontPkg) return
        lastFrontPkg = pkg
        // While the screen is off, only remember what's in front (the lock screen, a call, the camera…).
        if (screenOff) return
        if (pkg == self) {
            engine.onForeground(selfId)
            runTick()
            return
        }
        val app = appIdFor(pkg)
        if (!isActivity) {
            engine.onForeground(app)
            runTick()
            return
        }
        when (val v = engine.decide(app)) {
            is Verdict.Prompt -> {
                // Opened from a notification, Recents or another app: ask first.
                engine.onForeground(selfId)
                present(Overlay.Prompt(app, labelOf(app), v))
            }
            is Verdict.Blocked -> {
                engine.onForeground(selfId)
                present(Overlay.Blocked(app, labelOf(app), v))
            }
            else -> engine.onForeground(app)
        }
        runTick()
    }

    /** Stilla came back to the front: refresh what the home screen shows. */
    fun refreshHome() {
        _winsToday.value = engine.winsToday()
    }

    /** Unlocked: start counting again from whatever is in front now. */
    private fun wake() {
        if (!screenOff) return
        screenOff = false
        engine.resume()
        refreshWatcher()
        val pkg = lastFrontPkg ?: return
        lastFrontPkg = null
        onAppInFront(pkg)
    }

    /** Work-profile apps: if a package exists in one profile only, that's the one. */
    private fun appIdFor(pkg: String): AppId {
        val profiles = repo.apps.value.filter { it.key.packageName == pkg }.map { it.key.userSerial }.distinct()
        return appId(pkg, profiles.singleOrNull() ?: mySerial)
    }

    // ---- What the screens ask for ----

    /** Stilla's own list asks before opening an app. Returns true if it may open now. */
    fun requestOpen(app: AppId, label: String): Boolean {
        return when (val v = engine.decide(app)) {
            is Verdict.Prompt -> {
                present(Overlay.Prompt(app, label, v)); false
            }
            is Verdict.Blocked -> {
                present(Overlay.Blocked(app, label, v)); false
            }
            else -> true
        }
    }

    /** Opening the app now, with or without the watcher. */
    fun opened(app: AppId) {
        lastFrontPkg = app.packageName
        engine.onForeground(app)
        runTick()
    }

    /** Minutes picked on the prompt. */
    fun choose(minutes: Int) {
        val o = _overlay.value as? Overlay.Prompt ?: return
        engine.startSession(o.app, minutes)
        _overlay.value = null
        launch(o.app)
        save()
    }

    /** "Not now" on the prompt: a win. */
    fun notNow() {
        val o = _overlay.value as? Overlay.Prompt ?: return
        engine.backedOut(o.app)
        _overlay.value = null
        _winsToday.value = engine.winsToday()
        save()
    }

    /** +5 minutes after the extension card. */
    fun extend(minutes: Int) {
        val o = _overlay.value as? Overlay.TimeUp ?: return
        if (engine.session(o.app) != null) engine.extend(o.app, minutes) else engine.startSession(o.app, minutes)
        _overlay.value = null
        launch(o.app)
        save()
    }

    /** "Done" on the time's-up card. */
    fun done() {
        val o = _overlay.value ?: return
        if (o is Overlay.TimeUp) engine.endSession(o.app)
        _overlay.value = null
        save()
    }

    /** Stilla went to the background: forget a prompt or card nobody answered (no win counted). */
    fun dismissIfShowing() {
        // A card set a moment ago is the one we just bounced you to; Stilla's
        // own onStop can arrive right after it. Keep it.
        if (SystemClock.elapsedRealtime() - overlaySetAt < 2_000) return
        _overlay.value = null
    }

    fun dismissOverlay() {
        when (_overlay.value) {
            is Overlay.Prompt -> notNow()
            else -> done()
        }
    }

    fun blockApp(app: AppId, durationMs: Long) {
        engine.block(Target.of(app), BlockReason.MANUAL, durationMs)
        store.setBlocks(engine.blocks)
    }

    // ---- Timer ----

    private fun runTick() {
        val before = engine.blocks
        for (ev in engine.tick()) handle(ev)
        if (engine.blocks != before) store.setBlocks(engine.blocks)
        scheduleTick()
        save()
    }

    private fun scheduleTick() {
        handler.removeCallbacks(tickRunnable)
        val ms = engine.msUntilNextTick() ?: return
        handler.postDelayed(tickRunnable, ms.coerceIn(250, 6 * 60 * 60 * 1000L) + 50)
    }

    private fun handle(ev: EngineEvent) {
        val app = ev.app
        val card: Overlay? = when (ev) {
            is EngineEvent.KickOut -> Overlay.Blocked(app, labelOf(app), ev.verdict)
            is EngineEvent.TimeUp -> when (ev.onTimeOver) {
                OnTimeOver.EXIT_AND_BLOCK -> Overlay.Blocked(
                    app, labelOf(app),
                    engine.decide(app) as? Verdict.Blocked ?: Verdict.Blocked(BlockReason.TIME_OUT, null, step = 2),
                )
                OnTimeOver.EXTEND_MINDFULLY -> Overlay.TimeUp(app, labelOf(app), ev)
                OnTimeOver.REMIND -> { remind(app); null }
            }
            is EngineEvent.Remind -> { remind(app); null }
        }
        if (card != null) interrupt(card)
    }

    private var pendingCard: Overlay? = null
    private val retryInterrupt = Runnable { pendingCard?.let { pendingCard = null; interrupt(it) } }

    /**
     * Puts a card in front of the app you're in, when that's safe:
     *  - never during a call (phone or VoIP): try again in 30 s
     *  - without the watcher's permissions, Stilla can't know what's really in
     *    front (it could be BankID), so the card waits until you next open Stilla
     */
    private fun interrupt(card: Overlay) {
        if (inCall()) {
            pendingCard = card
            handler.removeCallbacks(retryInterrupt)
            handler.postDelayed(retryInterrupt, 30_000)
            return
        }
        if (!_watcherOn.value) {
            show(card)
            return
        }
        engine.onForeground(selfId)
        present(card)
    }

    /** A card that had to wait (no app watcher) is shown when you next open Stilla. */
    fun openPendingCard() {
        if (_overlay.value != null) openCardScreen()
    }

    private fun inCall(): Boolean = audio?.mode?.let { it != AudioManager.MODE_NORMAL } == true

    private fun show(card: Overlay) {
        overlaySetAt = SystemClock.elapsedRealtime()
        _overlay.value = card
    }

    private fun save() {
        runCatching { engineStore.save(engine.snapshot()) }
            .onFailure { Log.w("Stilla", "Couldn't save sessions", it) }
    }

    private fun remind(app: AppId) {
        runCatching {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        Toast.makeText(context, context.getString(R.string.remind_toast, labelOf(app)), Toast.LENGTH_LONG).show()
    }

    // ---- Helpers ----

    /**
     * Shows a card on its own screen, on top of whatever is in front. If an
     * essential app is really in front (a ringing alarm, a call), the card
     * waits until you next open Stilla instead.
     */
    private fun present(card: Overlay) {
        show(card)
        if (essentialInFront()) return
        openCardScreen()
    }

    /**
     * Asks Android directly, because [lastFrontPkg] can be a few moments old:
     * an alarm can start ringing just as you unlock.
     */
    private fun essentialInFront(): Boolean {
        val pkg = poller.latestInFront() ?: return false
        return pkg != self && isEssential(appIdFor(pkg))
    }

    private fun openCardScreen() {
        lastFrontPkg = self
        val intent = Intent(context, GuardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            // "Display over other apps" lets Stilla open this while another app is in front.
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("Stilla", "Couldn't show the card", e)
        }
    }

    private fun labelOf(app: AppId): String {
        val raw = repo.apps.value.firstOrNull { it.key.packageName == app.packageName && it.key.userSerial == app.profile }
        if (raw != null) prefs.state.value.renames[raw.key.encode()]?.let { return it }
        return raw?.label ?: app.packageName
    }

    private fun launch(app: AppId) {
        val raw = repo.apps.value.firstOrNull { it.key.packageName == app.packageName && it.key.userSerial == app.profile }
        try {
            if (raw != null) {
                launcherApps.startMainActivity(raw.component, raw.user, null, null)
            } else {
                context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
            opened(app)
        } catch (e: Exception) {
            Log.w("Stilla", "Couldn't open $app", e)
            Toast.makeText(context, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
