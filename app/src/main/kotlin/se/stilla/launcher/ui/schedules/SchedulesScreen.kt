package se.stilla.launcher.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import se.stilla.engine.Strictness
import se.stilla.launcher.R
import se.stilla.launcher.data.SavedSchedule
import se.stilla.launcher.data.ScheduleScope
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.guard.formatUntil
import se.stilla.launcher.ui.settings.ChoiceRow
import se.stilla.launcher.ui.settings.Heading
import se.stilla.launcher.ui.settings.SubIndent
import se.stilla.launcher.ui.settings.SubRow
import se.stilla.launcher.ui.settings.SubText
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle as DayStyle

private val EveryDay = DayOfWeek.values().toSet()
private val Weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val Weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

/** Your schedules, one row each, and a row to add one. */
@Composable
fun SchedulesScreen(
    schedules: List<SavedSchedule>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val now = Instant.now()
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .padding(top = 16.dp),
    ) {
        item { Heading(stringResource(R.string.schedules_title)) }
        item { SubText(stringResource(R.string.schedules_info)) }
        if (schedules.isEmpty()) {
            item { SubText(stringResource(R.string.schedules_none)) }
        }
        items(schedules, key = { it.id }) { saved ->
            val running = saved.schedule.activeWindow(now, zone) != null
            ScheduleRow(saved, running, locale) { onOpen(saved.id) }
        }
        item { SubRow(stringResource(R.string.schedules_new), onNew) }
    }
}

@Composable
private fun ScheduleRow(saved: SavedSchedule, running: Boolean, locale: Locale, onClick: () -> Unit) {
    val s = saved.schedule
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .quietClickable(onClick)
            .padding(start = StillaDimens.Gutter + SubIndent, end = StillaDimens.Gutter, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = s.name.ifBlank { stringResource(R.string.schedule_unnamed) },
            style = StillaType.SettingsRow.copy(
                fontSize = StillaType.Corner.fontSize,
                color = if (s.enabled) StillaColors.Text else StillaColors.TextQuiet,
            ),
        )
        Text(text = summary(saved, running, locale), style = StillaType.Body)
    }
}

/** "22:00–07:00 · varje dag · tidstjuvar · helt stopp · pågår nu" */
@Composable
private fun summary(saved: SavedSchedule, running: Boolean, locale: Locale): String {
    val s = saved.schedule
    val parts = mutableListOf(
        s.start.format(HourMinute) + "–" + s.end.format(HourMinute),
        daysText(s.days, locale),
        stringResource(if (saved.scope == ScheduleScope.ALL) R.string.schedule_scope_all_short else R.string.schedule_scope_watched_short),
        stringResource(if (s.strictness == Strictness.HARD) R.string.schedule_hard_short else R.string.schedule_soft_short),
    )
    when {
        !s.enabled -> parts += stringResource(R.string.schedule_off_short)
        running -> parts += stringResource(R.string.schedule_running_short)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun daysText(days: Set<DayOfWeek>, locale: Locale): String = when (days) {
    EveryDay -> stringResource(R.string.schedule_days_every)
    Weekdays -> stringResource(R.string.schedule_days_weekdays)
    Weekend -> stringResource(R.string.schedule_days_weekend)
    emptySet<DayOfWeek>() -> stringResource(R.string.schedule_days_none)
    else -> days.sorted().joinToString(" ") { dayName(it, locale) }
}

private fun dayName(day: DayOfWeek, locale: Locale): String =
    day.getDisplayName(DayStyle.SHORT, locale).trimEnd('.')

/**
 * One schedule: name, from–to, days, which apps and how strict. While it is
 * running it can be made stricter, but not looser (see LauncherViewModel.saveSchedule).
 */
@Composable
fun ScheduleEditScreen(
    initial: SavedSchedule,
    isNew: Boolean,
    runningUntil: Instant?,
    onSave: (SavedSchedule) -> Instant?,
    onDelete: () -> Instant?,
    onCancel: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    var name by remember(initial.id) { mutableStateOf(initial.schedule.name) }
    var start by remember(initial.id) { mutableStateOf(initial.schedule.start) }
    var end by remember(initial.id) { mutableStateOf(initial.schedule.end) }
    var days by remember(initial.id) { mutableStateOf(initial.schedule.days) }
    var scope by remember(initial.id) { mutableStateOf(initial.scope) }
    var strictness by remember(initial.id) { mutableStateOf(initial.schedule.strictness) }
    var enabled by remember(initial.id) { mutableStateOf(initial.schedule.enabled) }
    var refusedUntil by remember(initial.id) { mutableStateOf<Instant?>(null) }
    var needDay by remember(initial.id) { mutableStateOf(false) }

    fun draft() = initial.copy(
        schedule = initial.schedule.copy(
            name = name, start = start, end = end, days = days, strictness = strictness, enabled = enabled,
        ),
        scope = scope,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .padding(top = 16.dp),
    ) {
        item { Heading(stringResource(if (isNew) R.string.schedule_new_title else R.string.schedule_edit_title)) }
        if (runningUntil != null) {
            item { SubText(stringResource(R.string.schedule_running_info, formatUntil(runningUntil, locale)), quiet = false) }
        }

        item { SubText(stringResource(R.string.schedule_name)) }
        item { NameField(name) { name = it } }

        item { SubText(stringResource(R.string.schedule_from)) }
        item { TimeRow(start) { start = it } }
        item { SubText(stringResource(R.string.schedule_to)) }
        item { TimeRow(end) { end = it } }
        if (start == end) {
            item { SubText(stringResource(R.string.schedule_all_day)) }
        }

        item { SubText(stringResource(R.string.schedule_days)) }
        item { DaysRow(days, locale) { days = it; needDay = false } }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.schedule_days_every) to EveryDay,
                    stringResource(R.string.schedule_days_weekdays) to Weekdays,
                    stringResource(R.string.schedule_days_weekend) to Weekend,
                ),
                selected = days,
                onSelect = { days = it; needDay = false },
            )
        }
        if (needDay) {
            item { SubText(stringResource(R.string.schedule_need_day), quiet = false) }
        }

        item { SubText(stringResource(R.string.schedule_apps)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.schedule_scope_watched) to ScheduleScope.WATCHED,
                    stringResource(R.string.schedule_scope_all) to ScheduleScope.ALL,
                ),
                selected = scope,
                onSelect = { scope = it },
            )
        }
        item {
            SubText(
                stringResource(
                    if (scope == ScheduleScope.ALL) R.string.schedule_scope_all_info else R.string.schedule_scope_watched_info,
                ),
            )
        }

        item { SubText(stringResource(R.string.schedule_strictness)) }
        item {
            ChoiceRow(
                options = listOf(
                    stringResource(R.string.schedule_hard) to Strictness.HARD,
                    stringResource(R.string.schedule_soft) to Strictness.SOFT,
                ),
                selected = strictness,
                onSelect = { strictness = it },
            )
        }
        item {
            SubText(
                stringResource(if (strictness == Strictness.HARD) R.string.schedule_hard_info else R.string.schedule_soft_info),
            )
        }

        if (!isNew) {
            item { SubText(stringResource(R.string.schedule_state)) }
            item {
                ChoiceRow(
                    options = listOf(
                        stringResource(R.string.schedule_on) to true,
                        stringResource(R.string.schedule_off) to false,
                    ),
                    selected = enabled,
                    onSelect = { enabled = it },
                )
            }
        }

        refusedUntil?.let { until ->
            item { SubText(stringResource(R.string.schedule_refused, formatUntil(until, locale)), quiet = false) }
        }
        item {
            SubRow(stringResource(R.string.schedule_save)) {
                if (days.isEmpty()) {
                    needDay = true
                } else {
                    refusedUntil = onSave(draft())
                }
            }
        }
        if (!isNew) {
            item { SubRow(stringResource(R.string.schedule_delete)) { refusedUntil = onDelete() } }
        }
        item { SubRow(stringResource(R.string.cancel), onCancel) }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = StillaDimens.Gutter + SubIndent, end = StillaDimens.Gutter, top = 4.dp, bottom = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(30)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = StillaType.AppName,
            singleLine = true,
            cursorBrush = SolidColor(StillaColors.Text),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.schedule_name_hint),
                            style = StillaType.AppName.copy(color = StillaColors.TextQuiet),
                        )
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(StillaDimens.Hairline).background(StillaColors.Outline))
    }
}

/** "22:00   −1 h  −15  +15  +1 h": quiet steps instead of a clock dial. */
@Composable
private fun TimeRow(time: LocalTime, onChange: (LocalTime) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = StillaDimens.Gutter + SubIndent, end = StillaDimens.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time.format(HourMinute), style = StillaType.AppName, modifier = Modifier.padding(end = 12.dp))
        Step(stringResource(R.string.schedule_step_hour_minus)) { onChange(time.minusHours(1)) }
        Step("−15") { onChange(time.minusMinutes(15)) }
        Step("+15") { onChange(time.plusMinutes(15)) }
        Step(stringResource(R.string.schedule_step_hour_plus)) { onChange(time.plusHours(1)) }
    }
}

@Composable
private fun Step(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .quietClickable(onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = StillaType.Corner.copy(color = StillaColors.TextQuiet))
    }
}

/** Mon … Sun; the chosen days are bright and underlined, like the other choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaysRow(days: Set<DayOfWeek>, locale: Locale, onChange: (Set<DayOfWeek>) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = StillaDimens.Gutter + SubIndent - 8.dp, end = StillaDimens.Gutter),
    ) {
        DayOfWeek.values().forEach { day ->
            val on = day in days
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .quietClickable { onChange(if (on) days - day else days + day) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dayName(day, locale),
                    style = StillaType.SettingsRow.copy(
                        fontSize = StillaType.Corner.fontSize,
                        color = if (on) StillaColors.Text else StillaColors.TextQuiet,
                        textDecoration = if (on) TextDecoration.Underline else null,
                    ),
                )
            }
        }
    }
}
