package se.stilla.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import se.stilla.launcher.R
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.ClockFace
import se.stilla.launcher.ui.theme.LocalPalette
import se.stilla.launcher.ui.theme.LocalTextScale
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaType
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/**
 * The clock and battery on home, drawn the way the chosen theme likes.
 * Tapping the time calls [onClock]; tapping the date calls [onDate], in every face.
 */
@Composable
fun HomeClock(
    onClock: () -> Unit,
    onDate: () -> Unit,
    showBattery: Boolean,
    modifier: Modifier = Modifier,
) {
    val battery = if (showBattery) rememberBatteryPercent() else null
    when (LocalPalette.current.face) {
        ClockFace.RING_LINE -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            ClockRing(onClock, onDate)
            if (battery != null) BatteryLine(battery, Modifier.padding(top = 16.dp))
        }
        ClockFace.RING_DOT -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            ClockRing(onClock, onDate, battery = battery)
            if (battery != null) LowBatteryText(battery)
        }
        ClockFace.FRAME_DOTS -> FramedClock(onClock, onDate, battery, modifier)
        ClockFace.BARE_BAR -> BareClock(onClock, onDate, battery, modifier)
        ClockFace.SERIF_TEXT -> SerifClock(onClock, onDate, battery, modifier)
    }
}

/** Time and date in [timeStyle] and [dateStyle], each its own tap target. */
@Composable
private fun TimeAndDate(
    onClock: () -> Unit,
    onDate: () -> Unit,
    timeStyle: TextStyle = StillaType.Clock,
    dateStyle: TextStyle = StillaType.Date,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now = rememberMinuteClock()
    val is24 = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
    Text(text = time, style = timeStyle, modifier = Modifier.quietClickable(onClock))
    Text(text = date, style = dateStyle, maxLines = 1, softWrap = false, modifier = Modifier.quietClickable(onDate))
}

@Composable
private fun batteryLabel(percent: Int): String = stringResource(R.string.battery_level, percent)

/**
 * Black and Dark grey: the clock inside a thin ring. With [battery], a small dot
 * sits on the ring as far round (from the top, clockwise) as the charge.
 * [progress] (0..1) will later show today's screen time; for now it stays empty.
 */
@Composable
private fun ClockRing(
    onClock: () -> Unit,
    onDate: () -> Unit,
    progress: Float = 0f,
    battery: Int? = null,
) {
    // The ring grows with Stilla's text size, so XL never spills out of it.
    val ringSize = (184 * LocalTextScale.current).dp
    val ringColor = StillaColors.Outline
    val brightColor = StillaColors.Text
    val label = battery?.let { batteryLabel(it) }
    Box(
        modifier = Modifier
            .size(ringSize)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            val inset = 4.dp.toPx()
            val radius = size.minDimension / 2f - inset
            drawCircle(color = ringColor, radius = radius, style = Stroke(width = stroke))
            if (progress > 0f) {
                val d = radius * 2
                drawArc(
                    color = brightColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(d, d),
                    style = Stroke(width = stroke * 2),
                )
            }
            if (battery != null) {
                val angle = Math.toRadians(-90.0 + 360.0 * battery / 100.0)
                val dot = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
                drawCircle(color = brightColor, radius = 3.dp.toPx(), center = dot)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TimeAndDate(onClock, onDate)
        }
    }
}

/**
 * Silver: a light clock in a rounded frame, like a brushed watch face. The
 * battery is a row of ten small dots inside the frame, one per tenth of charge.
 */
@Composable
private fun FramedClock(onClock: () -> Unit, onDate: () -> Unit, battery: Int?, modifier: Modifier) {
    val scale = LocalTextScale.current
    val frame = StillaColors.Outline
    Column(
        modifier = modifier
            .width((252 * scale).dp)
            .border(1.dp, frame, RoundedCornerShape(28.dp))
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TimeAndDate(
            onClock, onDate,
            timeStyle = StillaType.Clock.copy(fontWeight = FontWeight.Light),
            dateStyle = StillaType.Date.copy(color = StillaColors.TextQuiet, letterSpacing = StillaType.Date.fontSize * 0.08f),
        )
        if (battery != null) {
            Spacer(Modifier.height(14.dp))
            BatteryDots(battery)
        }
    }
}

@Composable
private fun BatteryDots(percent: Int) {
    val on = StillaColors.Text
    val off = StillaColors.Outline
    val label = batteryLabel(percent)
    // Round up, so a nearly empty battery still shows one dot.
    val filled = ((percent + 9) / 10).coerceIn(0, 10)
    Canvas(
        modifier = Modifier
            .width(98.dp)
            .height(6.dp)
            .semantics { contentDescription = label },
    ) {
        val r = 2.dp.toPx()
        val step = (size.width - r * 2) / 9f
        for (i in 0 until 10) {
            val c = Offset(r + step * i, size.height / 2f)
            if (i < filled) drawCircle(on, r, c) else drawCircle(off, r - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
    }
}

/**
 * Graphite: a big, thin clock with nothing around it. The battery is a slim
 * upright bar to its left that fills from the bottom, like a thermometer.
 */
@Composable
private fun BareClock(onClock: () -> Unit, onDate: () -> Unit, battery: Int?, modifier: Modifier) {
    val scale = LocalTextScale.current
    Row(
        modifier = modifier.padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (battery != null) {
            val track = StillaColors.Outline
            val fill = StillaColors.Text
            val label = batteryLabel(battery)
            Canvas(
                modifier = Modifier
                    .width(4.dp)
                    .height((72 * scale).dp)
                    .semantics { contentDescription = label },
            ) {
                val x = size.width / 2f
                drawLine(track, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                val top = size.height * (1f - battery / 100f)
                if (battery > 0) drawLine(fill, Offset(x, top), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            }
        }
        Column(horizontalAlignment = Alignment.Start) {
            TimeAndDate(
                onClock, onDate,
                timeStyle = StillaType.Clock.copy(fontWeight = FontWeight.Thin, fontSize = StillaType.Clock.fontSize * 1.3f),
                dateStyle = StillaType.Date.copy(color = StillaColors.TextQuiet),
            )
        }
    }
}

/**
 * Paper: a serif clock between two short rules, like the title page of a book.
 * The battery is simply written out underneath, the way an e-reader does.
 */
@Composable
private fun SerifClock(onClock: () -> Unit, onDate: () -> Unit, battery: Int?, modifier: Modifier) {
    val rule = StillaColors.Outline
    Column(modifier = modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Rule(rule)
        Spacer(Modifier.height(18.dp))
        TimeAndDate(
            onClock, onDate,
            timeStyle = StillaType.Clock.copy(fontFamily = FontFamily.Serif),
            dateStyle = StillaType.Date.copy(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic),
        )
        Spacer(Modifier.height(18.dp))
        Rule(rule)
        if (battery != null) {
            Text(
                text = "$battery %",
                style = StillaType.Small.copy(fontFamily = FontFamily.Serif),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun Rule(color: Color) {
    Canvas(modifier = Modifier.width(48.dp).height(1.dp)) {
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
    }
}

/** The current time, updated each minute, but only while home is on screen. */
@Composable
private fun rememberMinuteClock(): ZonedDateTime {
    // Previews in Android Studio show a fixed time and skip the system broadcast.
    if (LocalInspectionMode.current) return PREVIEW_TIME
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    var now by remember { mutableStateOf(ZonedDateTime.now()) }

    DisposableEffect(visible) {
        if (!visible) return@DisposableEffect onDispose { }
        now = ZonedDateTime.now()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                now = ZonedDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}

private val PREVIEW_TIME: ZonedDateTime = ZonedDateTime.of(2026, 10, 7, 9, 41, 0, 0, java.time.ZoneId.of("Europe/Stockholm"))
