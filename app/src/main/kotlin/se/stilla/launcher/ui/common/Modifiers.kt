package se.stilla.launcher.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/** A tap target without the ripple, for the clock, date and corners. */
fun Modifier.quietClickable(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)
