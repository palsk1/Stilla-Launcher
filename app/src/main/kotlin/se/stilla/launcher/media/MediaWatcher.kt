package se.stilla.launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's playing right now. [title] is null when Stilla may only see that *something* plays. */
data class NowPlaying(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val packageName: String?,
)

/**
 * Music controls for home (Spotify and any other player).
 *
 * With "Notification access" Stilla reads the player's media session: song,
 * artist, play/pause state. Without it, it can still see that audio is playing
 * and send play/pause/next as media keys, the same as headphone buttons.
 */
class MediaWatcher(private val context: Context) {

    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private var controller: MediaController? = null
    private var listening = false
    /** Keep a paused player on home for a while, then let it go. */
    private var pausedSince = 0L

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick(currentSessions())
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Called when home comes to the front, and when notification access connects. */
    fun start() {
        if (!hasAccess()) {
            stopListening()
            fallback()
            return
        }
        if (!listening) {
            try {
                msm.addOnActiveSessionsChangedListener(sessionsChanged, component, handler)
                listening = true
            } catch (e: SecurityException) {
                Log.w("Stilla", "No media access", e)
                fallback()
                return
            }
        }
        pick(currentSessions())
    }

    fun playPause() {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        } else {
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun next() = controller?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    /** The player's own screen (Spotify's "now playing"), or null if it didn't set one. */
    fun openPlayerIntent() = controller?.sessionActivity

    // ----

    private fun currentSessions(): List<MediaController> =
        try { msm.getActiveSessions(component) } catch (e: SecurityException) { emptyList() }

    private fun pick(sessions: List<MediaController>) {
        // A playing session first; otherwise the most recent one (Android sorts by recency).
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _now.value = null
            return
        }
        val playing = c.playbackState?.state.let {
            it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
        }
        val now = SystemClock.elapsedRealtime()
        if (playing) pausedSince = 0L else if (pausedSince == 0L) pausedSince = now
        if (!playing && now - pausedSince > KEEP_PAUSED_MS) {
            _now.value = null
            return
        }
        val meta = c.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) {
            _now.value = null
            return
        }
        _now.value = NowPlaying(
            title = title,
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            playing = playing,
            packageName = c.packageName,
        )
    }

    private fun fallback() {
        val active = audio?.isMusicActive == true
        _now.value = if (active) NowPlaying(null, null, playing = true, packageName = null) else null
    }

    private fun stopListening() {
        if (listening) runCatching { msm.removeOnActiveSessionsChangedListener(sessionsChanged) }
        listening = false
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun key(code: Int) {
        val time = SystemClock.uptimeMillis()
        audio?.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        audio?.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
        // Without notification access, re-check whether audio plays after the player reacts.
        if (!hasAccess()) handler.postDelayed({ fallback() }, 600)
    }

    private companion object {
        const val KEEP_PAUSED_MS = 30 * 60 * 1000L
    }
}
