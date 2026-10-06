package se.stilla.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import se.stilla.launcher.ui.common.AppRowContent
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import se.stilla.launcher.AppEntry
import se.stilla.launcher.R
import se.stilla.launcher.media.NowPlaying
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.LocalTextScale
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    isDefaultHome: Boolean,
    onOpenApps: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onPhone: () -> Unit,
    onCamera: () -> Unit,
    onClock: () -> Unit,
    onDate: () -> Unit,
    onSetDefault: () -> Unit,
    watcherPaused: Boolean = false,
    onFixWatcher: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onMoveFavorite: (AppEntry, Int) -> Unit = { _, _ -> },
    nowPlaying: NowPlaying? = null,
    onOpenPlayer: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    showBattery: Boolean = true,
) {
    val workSuffix = stringResource(R.string.work_suffix)
    val openApps by rememberUpdatedState(onOpenApps)
    val reorder = remember { Reorder() }
    val swipeDown by rememberUpdatedState(onSwipeDown)
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            // Swipe up or left anywhere: app list. Swipe down: notifications.
            // Watches touches before the rows do (Initial pass), so a swipe that
            // starts on a favorite works too, and fires as soon as the finger has
            // moved far enough rather than on release.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // A favorite is being dragged to a new place: not a swipe.
                        if (reorder.key != null) continue
                        if (fired) {
                            change.consume()
                            continue
                        }
                        val d = change.position - down.position
                        val vertical = abs(d.y) >= abs(d.x)
                        when {
                            vertical && d.y < -swipeThreshold -> { fired = true; openApps() }
                            vertical && d.y > swipeThreshold -> { fired = true; swipeDown() }
                            !vertical && d.x < -swipeThreshold -> { fired = true; openApps() }
                        }
                        if (fired) change.consume()
                    }
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            if (watcherPaused || !isDefaultHome) {
                // One small line while something in setup is missing; the checklist explains.
                Text(
                    text = stringResource(R.string.setup_unfinished),
                    style = StillaType.Small,
                    modifier = Modifier
                        .quietClickable(onFixWatcher)
                        .padding(horizontal = StillaDimens.Gutter, vertical = 12.dp),
                )
            }

            Spacer(Modifier.height(32.dp))
            HomeClock(
                onClock = onClock,
                onDate = onDate,
                showBattery = showBattery,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (nowPlaying != null) {
                NowPlayingBar(
                    now = nowPlaying,
                    onOpen = onOpenPlayer,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            // Favorites follow right under the clock; the free space goes below them.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(modifier = Modifier.padding(top = 40.dp)) {
                    if (favorites.isEmpty()) {
                        Text(
                            text = stringResource(R.string.home_hint),
                            style = StillaType.Body,
                            modifier = Modifier.padding(horizontal = StillaDimens.Gutter, vertical = 24.dp),
                        )
                    }
                    Favorites(
                        favorites = favorites,
                        workSuffix = workSuffix,
                        reorder = reorder,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        onMove = onMoveFavorite,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(StillaDimens.RowHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.phone),
                    style = StillaType.Corner,
                    modifier = Modifier.quietClickable(onPhone).padding(horizontal = StillaDimens.Gutter, vertical = 16.dp),
                )
                Text(
                    text = stringResource(R.string.camera),
                    style = StillaType.Corner,
                    modifier = Modifier.quietClickable(onCamera).padding(horizontal = StillaDimens.Gutter, vertical = 16.dp),
                )
            }
        }
    }
}

/** The favorite being dragged to a new place, and how far it has moved. */
private class Reorder {
    var key by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    var rowHeight by mutableFloatStateOf(1f)
}

/**
 * Favorites on home. Tap opens. Hold and drag moves it up or down; hold and
 * let go without moving shows the app's menu.
 */
@Composable
private fun Favorites(
    favorites: List<AppEntry>,
    workSuffix: String,
    reorder: Reorder,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onMove: (AppEntry, Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val menuSlop = with(LocalDensity.current) { 12.dp.toPx() }
    val list by rememberUpdatedState(favorites)
    val launch by rememberUpdatedState(onLaunch)
    val menu by rememberUpdatedState(onLongPress)
    val move by rememberUpdatedState(onMove)

    fun targetIndex(from: Int): Int =
        (from + (reorder.offset / reorder.rowHeight).roundToInt()).coerceIn(0, list.lastIndex)

    val from = favorites.indexOfFirst { it.key == reorder.key }
    val to = if (from < 0) -1 else targetIndex(from)

    favorites.forEachIndexed { i, entry ->
        key(entry.key) {
            val dragging = entry.key == reorder.key
            val shift = when {
                from < 0 || dragging -> 0f
                from < to && i in (from + 1)..to -> -reorder.rowHeight
                from > to && i in to until from -> reorder.rowHeight
                else -> 0f
            }
            val animatedShift by animateFloatAsState(shift, label = "shift")
            AppRowContent(
                entry = entry,
                workSuffix = workSuffix,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .onSizeChanged { if (it.height > 0) reorder.rowHeight = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = if (dragging) reorder.offset else animatedShift
                        alpha = if (dragging) 0.85f else 1f
                    }
                    .pointerInput(entry.key) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            // Up before the long-press time: a tap. Cancelled (a swipe took over): nothing.
                            val early: Any? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation() ?: Unit
                            }
                            if (early is PointerInputChange) {
                                early.consume()
                                list.firstOrNull { it.key == entry.key }?.let(launch)
                                return@awaitEachGesture
                            }
                            if (early != null) return@awaitEachGesture

                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            reorder.offset = 0f
                            reorder.key = entry.key
                            drag(down.id) { change ->
                                reorder.offset += change.positionChange().y
                                change.consume()
                            }
                            val current = list.firstOrNull { it.key == entry.key }
                            val start = list.indexOfFirst { it.key == entry.key }
                            if (current != null && start >= 0) {
                                if (abs(reorder.offset) < menuSlop) {
                                    menu(current)
                                } else {
                                    val target = targetIndex(start)
                                    if (target != start) move(current, target)
                                }
                            }
                            reorder.key = null
                            reorder.offset = 0f
                        }
                    },
            )
        }
    }
}
