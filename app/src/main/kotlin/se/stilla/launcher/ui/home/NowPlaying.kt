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
            Control(stringResource(R.string.media_previous), onPrevious) { previous() }
            Control(stringResource(if (now.playing) R.string.media_pause else R.string.media_play), onPlayPause) {
                if (now.playing) pause() else play()
            }
            Control(stringResource(R.string.media_next), onNext) { next() }
        }
    }
}

@Composable
private fun Control(label: String, onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    val glyph = (14 * LocalTextScale.current).dp
    Box(
        modifier = Modifier
            .quietClickable(onClick)
            .semantics { contentDescription = label }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(glyph), onDraw = draw)
    }
}

private fun DrawScope.triangle(left: Float, right: Float, pointsRight: Boolean) {
    val path = Path().apply {
        if (pointsRight) {
            moveTo(left, 0f); lineTo(right, size.height / 2f); lineTo(left, size.height)
        } else {
            moveTo(right, 0f); lineTo(left, size.height / 2f); lineTo(right, size.height)
        }
        close()
    }
    drawPath(path, StillaColors.Text)
}

private fun DrawScope.bar(x: Float, width: Float) =
    drawRect(StillaColors.Text, topLeft = Offset(x, 0f), size = Size(width, size.height))

private fun DrawScope.play() = triangle(size.width * 0.15f, size.width * 0.95f, pointsRight = true)

private fun DrawScope.pause() {
    val w = size.width * 0.28f
    bar(size.width * 0.1f, w)
    bar(size.width * 0.62f, w)
}

private fun DrawScope.next() {
    triangle(0f, size.width * 0.75f, pointsRight = true)
    bar(size.width * 0.8f, size.width * 0.14f)
}

private fun DrawScope.previous() {
    bar(size.width * 0.06f, size.width * 0.14f)
    triangle(size.width * 0.25f, size.width, pointsRight = false)
}
