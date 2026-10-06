package se.stilla.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import se.stilla.launcher.R
import se.stilla.launcher.ui.theme.LocalTextScale
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaType

/** At or below this, the number shows under the line too. */
private const val LOW_PERCENT = 20

/**
 * Battery as one short horizontal line under the clock: a faint track, and a
 * brighter stretch from the left as long as the charge. No icon, no number,
 * until the battery runs low; then the percent appears underneath.
 */
@Composable
fun BatteryLine(modifier: Modifier = Modifier) {
    val percent = rememberBatteryPercent() ?: return
    val track = StillaColors.Outline
    val fill = StillaColors.Text
    val label = stringResource(R.string.battery_level, percent)
    Column(
        modifier = modifier.semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.width((64 * LocalTextScale.current).dp).height(4.dp)) {
            val y = size.height / 2f
            val thin = 1.dp.toPx()
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = thin)
            val end = size.width * percent / 100f
            if (end > 0f) {
                drawLine(fill, Offset(0f, y), Offset(end, y), strokeWidth = thin * 2, cap = StrokeCap.Butt)
            }
        }
        if (percent <= LOW_PERCENT) {
            Text(text = "$percent %", style = StillaType.Small, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** The battery charge in percent, kept up to date while home is on screen. Null if unknown. */
@Composable
private fun rememberBatteryPercent(): Int? {
    if (LocalInspectionMode.current) return 72
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    var percent by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(visible) {
        if (!visible) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.batteryPercent()?.let { percent = it }
            }
        }
        // The battery broadcast is sticky: registering hands back the latest value at once.
        val latest = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        latest?.batteryPercent()?.let { percent = it }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

private fun Intent.batteryPercent(): Int? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return (level * 100 / scale).coerceIn(0, 100)
}
