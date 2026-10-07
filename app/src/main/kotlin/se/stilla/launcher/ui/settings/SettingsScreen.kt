package se.stilla.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import se.stilla.launcher.AppEntry
import se.stilla.launcher.LauncherState
import se.stilla.launcher.R
import se.stilla.launcher.data.ThemeChoice
import se.stilla.engine.OnTimeOver
import se.stilla.launcher.ui.guard.formatUntil
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType

internal val SubIndent = 40.dp

/** Phase 1 settings: home app, hidden apps, essentials, about. More sections arrive with each phase. */
@Composable
fun SettingsScreen(
    state: LauncherState,
    isDefaultHome: Boolean,
    versionName: String,
    onSetDefault: () -> Unit,
    onUnhide: (AppEntry) -> Unit,
    theme: ThemeChoice,
    textScale: Float,
    onTheme: (ThemeChoice) -> Unit,
    onTextScale: (Float) -> Unit,
    watcherOn: Boolean,
    onOpenWatcherSettings: () -> Unit,
    onTimeOver: (OnTimeOver) -> Unit,
    onOpenSetup: () -> Unit = {},
    hideStatusBar: Boolean = true,
    onHideStatusBar: (Boolean) -> Unit = {},
    showBattery: Boolean = true,
    onShowBattery: (Boolean) -> Unit = {},
    onOpenSchedules: () -> Unit = {},
    foldersOn: Boolean = false,
    onFoldersOn: (Boolean) -> Unit = {},
) {
    val hidden = state.all.filter { it.hidden }
    val watched = state.all.filter { it.watched }
    val now = Instant.now()
    val locale = LocalConfiguration.current.locales[0]
    // One line per blocked app, with the latest end.
    val blocked = state.all.mapNotNull { entry ->
        val end = state.rules.blocks
            .filter { it.target.covers(entry.appId) && it.endsAt().isAfter(now) }
            .maxOfOrNull { it.endsAt() }
        if (end == null || entry.essential) null else entry to end
    }
    val essentials = state.all.filter { it.essential }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .padding(top = 16.dp),
    ) {
        item { Heading(stringResource(R.string.settings_home_app)) }
        item {
            if (isDefaultHome) {
                SubText(stringResource(R.string.settings_is_default))
            } else {
                SubRow(stringResource(R.string.settings_make_default), onSetDefault)
            }
        }

        item { Heading(stringResource(R.string.settings_mindful)) }
        item {
            if (watcherOn) {
                SubText(stringResource(R.string.settings_watcher_on))
            } else {
                SubRow(stringResource(R.string.settings_watcher_off), onOpenWatcherSettings)
                SubText(stringResource(R.string.settings_watcher_off_info))
            }
        }
        item { SubText(stringResource(R.string.settings_time_over)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.settings_time_over_extend) to OnTimeOver.EXTEND_MINDFULLY,
                    stringResource(R.string.settings_time_over_block) to OnTimeOver.EXIT_AND_BLOCK,
                    stringResource(R.string.settings_time_over_remind) to OnTimeOver.REMIND,
                ),
                selected = state.rules.onTimeOver,
                onSelect = onTimeOver,
            )
        }
        item {
            val n = state.rules.schedules.size
            SubRow(
                if (n == 0) stringResource(R.string.settings_schedules)
                else stringResource(R.string.settings_schedules_count, n),
                onOpenSchedules,
            )
        }
        item { SubText(stringResource(R.string.settings_watched)) }
        item { SubText(stringResource(R.string.settings_watched_info)) }
        if (watched.isEmpty()) {
            item { SubText(stringResource(R.string.settings_watched_none)) }
        } else {
            items(watched, key = { "watched:" + it.key }) { SubText(it.label, quiet = false) }
        }
        if (blocked.isNotEmpty()) {
            item { SubText(stringResource(R.string.settings_blocks)) }
            items(blocked, key = { "blocked:" + it.first.key }) { (entry, end) ->
                SubText(stringResource(R.string.settings_block_row, entry.label, formatUntil(end, locale)), quiet = false)
            }
        }

        item { Heading(stringResource(R.string.settings_display)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.settings_theme_black) to ThemeChoice.BLACK,
                    stringResource(R.string.settings_theme_grey) to ThemeChoice.DARK_GREY,
                    stringResource(R.string.settings_theme_silver) to ThemeChoice.SILVER,
                    stringResource(R.string.settings_theme_graphite) to ThemeChoice.GRAPHITE,
                    stringResource(R.string.settings_theme_paper) to ThemeChoice.PAPER,
                ),
                selected = theme,
                onSelect = onTheme,
            )
        }
        item { SubText(stringResource(R.string.settings_status_bar)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.settings_status_bar_hidden) to true,
                    stringResource(R.string.settings_status_bar_shown) to false,
                ),
                selected = hideStatusBar,
                onSelect = onHideStatusBar,
            )
        }
        item { SubText(stringResource(R.string.settings_folders)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.schedule_on) to true,
                    stringResource(R.string.schedule_off) to false,
                ),
                selected = foldersOn,
                onSelect = onFoldersOn,
            )
        }
        item { SubText(stringResource(R.string.settings_battery)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.settings_status_bar_shown) to true,
                    stringResource(R.string.settings_status_bar_hidden) to false,
                ),
                selected = showBattery,
                onSelect = onShowBattery,
            )
        }
        item { SubText(stringResource(R.string.settings_text_size)) }
        item {
            ChoiceRow(
                options = TextSizes.map { (label, scale) -> label to scale },
                selected = TextSizes.minByOrNull { kotlin.math.abs(it.second - textScale) }?.second ?: 1f,
                onSelect = onTextScale,
            )
        }

        item { Heading(stringResource(R.string.settings_hidden)) }
        if (hidden.isEmpty()) {
            item { SubText(stringResource(R.string.settings_hidden_none)) }
        } else {
            item { SubText(stringResource(R.string.settings_hidden_tap)) }
            items(hidden, key = { "hidden:" + it.key }) { entry -> SubRow(entry.label) { onUnhide(entry) } }
        }

        item { Heading(stringResource(R.string.settings_essentials)) }
        item { SubText(stringResource(R.string.settings_essentials_info)) }
        items(essentials, key = { "ess:" + it.key }) { entry -> SubText(entry.label, quiet = false) }

        item { Heading(stringResource(R.string.settings_setup)) }
        item { SubRow(stringResource(R.string.settings_setup_open), onOpenSetup) }

        item { Heading(stringResource(R.string.settings_about)) }
        item { SubText(stringResource(R.string.settings_about_text, versionName)) }
    }
}

private val TextSizes = listOf("S" to 0.85f, "M" to 1f, "L" to 1.15f, "XL" to 1.3f)

/** A row of text choices; the chosen one is bright and underlined, the rest quiet. Wraps when it doesn't fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(start = StillaDimens.Gutter + SubIndent - 8.dp, end = StillaDimens.Gutter),
    ) {
        options.forEach { (label, value) ->
            val isOn = value == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .quietClickable { onSelect(value) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = StillaType.SettingsRow.copy(
                        fontSize = StillaType.Corner.fontSize,
                        color = if (isOn) StillaColors.Text else StillaColors.TextQuiet,
                        textDecoration = if (isOn) TextDecoration.Underline else null,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun Heading(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = StillaDimens.RowHeight).padding(horizontal = StillaDimens.Gutter),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(text = text, style = StillaType.SettingsRow, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
internal fun SubRow(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .quietClickable(onClick)
            .padding(start = StillaDimens.Gutter + SubIndent, end = StillaDimens.Gutter),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = StillaType.SettingsRow.copy(fontSize = StillaType.Corner.fontSize))
    }
}

@Composable
internal fun SubText(text: String, quiet: Boolean = true) {
    Text(
        text = text,
        style = StillaType.Body.copy(color = if (quiet) StillaColors.TextQuiet else StillaColors.Text),
        modifier = Modifier.padding(start = StillaDimens.Gutter + SubIndent, end = StillaDimens.Gutter, top = 4.dp, bottom = 4.dp),
    )
}
