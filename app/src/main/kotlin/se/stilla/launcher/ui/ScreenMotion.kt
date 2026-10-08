package se.stilla.launcher.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import se.stilla.launcher.Screen

/** Short enough to never feel slow, long enough to see where a screen came from. */
private const val MotionMs = 260

private val Spec = tween<IntOffset>(MotionMs, easing = FastOutSlowInEasing)

/** How far from home a screen is; going deeper slides in, going back slides out. */
private fun depth(s: Screen) = when (s) {
    Screen.Home -> 0
    Screen.Apps, Screen.Folders -> 1
    Screen.Settings, Screen.Folder -> 2
    Screen.Help, Screen.Schedules, Screen.Setup -> 3
    Screen.ScheduleEdit -> 4
}

private fun isFolder(s: Screen) = s == Screen.Folders || s == Screen.Folder

/**
 * Screens move the way your finger did: swipe left and the app list comes in from the
 * right, swipe up and the folders rise from below. Going back runs it in reverse, with
 * the screen you leave sliding off on top. The screen underneath moves a quarter as far,
 * which gives a little depth without any extra drawing.
 */
internal fun AnimatedContentTransitionScope<Screen>.screenMotion(): ContentTransform {
    val from = initialState
    val to = targetState
    val vertical = (from == Screen.Home && isFolder(to)) || (isFolder(from) && to == Screen.Home)
    val forward = depth(to) > depth(from) || (isFolder(from) && to == Screen.Apps)
    val transform = when {
        vertical && forward ->
            slideInVertically(Spec) { it } togetherWith slideOutVertically(Spec) { -it / 4 }
        vertical ->
            slideInVertically(Spec) { -it / 4 } togetherWith slideOutVertically(Spec) { it }
        forward ->
            slideInHorizontally(Spec) { it } togetherWith slideOutHorizontally(Spec) { -it / 4 }
        else ->
            slideInHorizontally(Spec) { -it / 4 } togetherWith slideOutHorizontally(Spec) { it }
    }
    // Going back, the screen you leave stays on top while it slides away.
    if (!forward) transform.targetContentZIndex = -1f
    return transform using null
}
