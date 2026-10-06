package se.stilla.launcher.ui.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import se.stilla.engine.BlockReason
import se.stilla.launcher.R
import se.stilla.launcher.guard.Overlay
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Counts down from [seconds] once per second; restarts when [key] changes. */
@Composable
private fun rememberCountdown(key: Any, seconds: Int): Int {
    var left by remember(key) { mutableIntStateOf(seconds) }
    LaunchedEffect(key) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    return left
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .padding(horizontal = StillaDimens.Gutter),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** A rounded outlined text button, the plan's "time chip". */
@Composable
private fun Chip(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) StillaColors.Text else StillaColors.TextQuiet
    Box(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .widthIn(min = 72.dp)
            .border(StillaDimens.Hairline, if (enabled) StillaColors.Outline else StillaColors.Surface, RoundedCornerShape(StillaDimens.ChipRadius))
            .then(if (enabled) Modifier.quietClickable(onClick) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = StillaType.Corner.copy(color = color), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptCard(o: Overlay.Prompt, onChoose: (Int) -> Unit, onNotNow: () -> Unit) {
    val v = o.verdict
    val wait = rememberCountdown(o, v.waitSec)
    val ready = wait == 0
    val maxMin = v.minutesLeftToday ?: 120
    var custom by remember(o) { mutableIntStateOf(20.coerceAtMost(maxMin).coerceAtLeast(1)) }

    Card {
        Text(stringResource(R.string.prompt_question, o.label), style = StillaType.AppName)
        v.softSchedule?.let { Text(stringResource(R.string.prompt_soft_schedule, it), style = StillaType.Body) }
        v.opensLeft?.let { Text(stringResource(R.string.prompt_opens_left, it), style = StillaType.Body) }
        v.minutesLeftToday?.let { Text(stringResource(R.string.prompt_minutes_left, it), style = StillaType.Body) }
        Spacer(Modifier.height(8.dp))
        if (!ready) {
            Text(stringResource(R.string.prompt_breathe, wait), style = StillaType.Corner.copy(color = StillaColors.TextQuiet))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(5, 10, 15).forEach { m ->
                Chip(stringResource(R.string.prompt_minutes, m), enabled = ready && m <= maxMin) { onChoose(m) }
            }
        }
        // Your own number: − 20 min +, then "Open 20 min" on its own line so nothing wraps.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("−", enabled = ready && custom > 1) { custom = (custom - 5).coerceAtLeast(1) }
            Text(stringResource(R.string.prompt_minutes, custom), style = StillaType.Corner, maxLines = 1)
            Chip("+", enabled = ready && custom < maxMin) { custom = (custom + 5).coerceAtMost(maxMin) }
        }
        Chip(stringResource(R.string.prompt_open_custom, custom), enabled = ready) { onChoose(custom) }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.prompt_not_now),
            style = StillaType.Corner,
            modifier = Modifier.quietClickable(onNotNow).padding(vertical = 12.dp),
        )
    }
}

@Composable
fun BlockedCard(o: Overlay.Blocked, onBackHome: () -> Unit) {
    val v = o.verdict
    val locale = LocalConfiguration.current.locales[0]
    Card {
        Text(stringResource(R.string.blocked_title, o.label), style = StillaType.AppName)
        v.until?.let { Text(stringResource(R.string.blocked_until, formatUntil(it, locale)), style = StillaType.Corner) }
        val reason = when (v.reason) {
            BlockReason.MANUAL -> stringResource(R.string.blocked_reason_manual)
            BlockReason.TIME_OUT -> stringResource(R.string.blocked_reason_time_out)
            BlockReason.SCHEDULE -> stringResource(R.string.blocked_reason_schedule, v.label.orEmpty())
            BlockReason.DAILY_BUDGET -> stringResource(R.string.blocked_reason_budget)
            BlockReason.OPEN_LIMIT -> stringResource(R.string.blocked_reason_opens)
        }
        Text(reason, style = StillaType.Body)
        Spacer(Modifier.height(24.dp))
        Chip(stringResource(R.string.blocked_back_home), onClick = onBackHome)
    }
}

@Composable
fun TimeUpCard(o: Overlay.TimeUp, onExtend: () -> Unit, onDone: () -> Unit) {
    val wait = rememberCountdown(o, o.event.extensionWaitSec)
    Card {
        Text(stringResource(R.string.timeup_title, o.label), style = StillaType.AppName)
        Text(stringResource(R.string.timeup_today, o.event.minutesToday), style = StillaType.Body)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip(stringResource(R.string.timeup_done), onClick = onDone)
            if (wait > 0) {
                Chip(stringResource(R.string.timeup_extend_wait, wait), enabled = false) {}
            } else {
                Chip(stringResource(R.string.timeup_extend), onClick = onExtend)
            }
        }
    }
}

/** "07:00" today or tomorrow; "tis 13 okt 12:00" further away. */
fun formatUntil(at: Instant, locale: java.util.Locale): String {
    val zone = ZoneId.systemDefault()
    val pattern = if (Duration.between(Instant.now(), at).toHours() < 20) "HH:mm" else "EEE d MMM HH:mm"
    return at.atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
}
