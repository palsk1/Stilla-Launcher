package se.stilla.launcher.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import se.stilla.launcher.AppEntry
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType

/** One app name as a big text row. Tap opens, long-press shows the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    entry: AppEntry,
    workSuffix: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = StillaDimens.Gutter,
) {
    AppRowContent(
        entry = entry,
        workSuffix = workSuffix,
        startPadding = startPadding,
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = entry.label),
    )
}

/** The look of an app row without any touch handling (home adds its own, for drag and drop). */
@Composable
fun AppRowContent(
    entry: AppEntry,
    workSuffix: String,
    modifier: Modifier = Modifier,
    startPadding: Dp = StillaDimens.Gutter,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StillaDimens.RowHeight)
            .then(modifier)
            .padding(start = startPadding, end = StillaDimens.Gutter / 2),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = buildAnnotatedString {
                append(entry.label)
                entry.maker?.let { maker ->
                    withStyle(SpanStyle(color = StillaColors.TextQuiet, fontSize = 13.sp)) {
                        append("  · $maker")
                    }
                }
                if (entry.raw.isWork) {
                    withStyle(SpanStyle(color = StillaColors.TextQuiet, fontSize = 13.sp)) {
                        append("  · $workSuffix")
                    }
                }
            },
            style = StillaType.AppName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
