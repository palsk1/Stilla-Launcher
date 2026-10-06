package se.stilla.launcher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.stilla.launcher.R
import se.stilla.launcher.media.NowPlaying
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalTextScale
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType

/** Song and three thin controls under the clock, only while something plays (or was just paused). */
@Composable
fun NowPlayingBar(
    now: NowPlaying,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (now.title != null) {
            Text(
                text = listOfNotNull(now.title, now.artist?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = StillaType.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .quietClickable(onOpen)
                    .padding(horizontal = StillaDimens.Gutter, vertical = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Control(stringResource(R.string.media_previous), onPrevious) { previous(it) }
            Control(stringResource(if (now.playing) R.string.media_pause else R.string.media_play), onPlayPause) {
                if (now.playing) pause(it) else play(it)
            }
            Control(stringResource(R.string.media_next), onNext) { next(it) }
        }
    }
}

@Composable
private fun Control(label: String, onClick: () -> Unit, draw: DrawScope.(Color) -> Unit) {
    val glyph = (14 * LocalTextScale.current).dp
    val color = StillaColors.Text
    Box(
        modifier = Modifier
            .quietClickable(onClick)
            .semantics { contentDescription = label }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(glyph)) { draw(color) }
    }
}

private fun DrawScope.triangle(left: Float, right: Float, pointsRight: Boolean, color: Color) {
    val path = Path().apply {
        if (pointsRight) {
            moveTo(left, 0f); lineTo(right, size.height / 2f); lineTo(left, size.height)
        } else {
            moveTo(right, 0f); lineTo(left, size.height / 2f); lineTo(right, size.height)
        }
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.bar(x: Float, width: Float, color: Color) =
    drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))

private fun DrawScope.play(color: Color) = triangle(size.width * 0.15f, size.width * 0.95f, pointsRight = true, color)

private fun DrawScope.pause(color: Color) {
    val w = size.width * 0.28f
    bar(size.width * 0.1f, w, color)
    bar(size.width * 0.62f, w, color)
}

private fun DrawScope.next(color: Color) {
    triangle(0f, size.width * 0.75f, pointsRight = true, color)
    bar(size.width * 0.8f, size.width * 0.14f, color)
}

private fun DrawScope.previous(color: Color) {
    bar(size.width * 0.06f, size.width * 0.14f, color)
    triangle(size.width * 0.25f, size.width, pointsRight = false, color)
}
