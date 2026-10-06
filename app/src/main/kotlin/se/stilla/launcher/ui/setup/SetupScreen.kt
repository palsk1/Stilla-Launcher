package se.stilla.launcher.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import se.stilla.launcher.R
import se.stilla.launcher.setup.SetupStatus
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType

/**
 * The setup checklist. Each step opens the exact Android page it needs and
 * ticks itself off when you come back.
 */
@Composable
fun SetupScreen(
    status: SetupStatus,
    onHome: () -> Unit,
    onOverlay: () -> Unit,
    onUsage: () -> Unit,
    onBattery: () -> Unit,
    onMedia: () -> Unit,
    onRestricted: () -> Unit = {},
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 48.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = StillaType.AppName,
            modifier = Modifier.padding(horizontal = StillaDimens.Gutter),
        )
        Text(
            text = stringResource(R.string.setup_intro),
            style = StillaType.Body,
            modifier = Modifier.padding(horizontal = StillaDimens.Gutter, vertical = 12.dp),
        )
        Spacer(Modifier.height(16.dp))

        if (status.restricted && (!status.usage || !status.media)) {
            // Installed from a file: the switches below start out grey.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .quietClickable(onRestricted)
                    .padding(horizontal = StillaDimens.Gutter, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_restricted_title),
                    style = StillaType.Corner.copy(textDecoration = TextDecoration.Underline),
                )
                Text(
                    text = stringResource(R.string.setup_restricted_hint),
                    style = StillaType.Body,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Step(
            number = 1,
            title = stringResource(R.string.setup_home_title),
            hint = stringResource(R.string.setup_home_hint),
            done = status.isHome,
            onClick = onHome,
        )
        Step(
            number = 2,
            title = stringResource(R.string.setup_overlay_title),
            hint = stringResource(R.string.setup_overlay_hint),
            done = status.overlay,
            onClick = onOverlay,
        )
        Step(
            number = 3,
            title = stringResource(R.string.setup_usage_title),
            hint = stringResource(R.string.setup_usage_hint),
            done = status.usage,
            onClick = onUsage,
        )
        Step(
            number = 4,
            title = stringResource(R.string.setup_battery_title),
            hint = stringResource(R.string.setup_battery_hint),
            done = status.batteryFree,
            onClick = onBattery,
        )
        Step(
            number = 5,
            title = stringResource(R.string.setup_media_title),
            hint = stringResource(R.string.setup_media_hint),
            done = status.media,
            onClick = onMedia,
        )

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StillaDimens.RowHeight)
                .quietClickable(onFinish)
                .padding(horizontal = StillaDimens.Gutter),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(if (status.requiredDone) R.string.setup_finish else R.string.setup_later),
                style = StillaType.SettingsRow.copy(
                    color = if (status.requiredDone) StillaColors.Text else StillaColors.TextQuiet,
                    textDecoration = if (status.requiredDone) TextDecoration.Underline else null,
                ),
            )
        }
    }
}

private val MarkWidth = 40.dp

@Composable
private fun Step(number: Int, title: String, hint: String, done: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = StillaDimens.RowHeight)
            .quietClickable(onClick)
            .padding(horizontal = StillaDimens.Gutter, vertical = 12.dp),
    ) {
        Text(
            text = if (done) "✓" else number.toString(),
            style = StillaType.SettingsRow.copy(color = if (done) StillaColors.TextQuiet else StillaColors.Text),
            modifier = Modifier.width(MarkWidth),
        )
        Column {
            Text(
                text = title,
                style = StillaType.SettingsRow.copy(color = if (done) StillaColors.TextQuiet else StillaColors.Text),
            )
            Text(
                text = if (done) stringResource(R.string.setup_done) else hint,
                style = StillaType.Body,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
