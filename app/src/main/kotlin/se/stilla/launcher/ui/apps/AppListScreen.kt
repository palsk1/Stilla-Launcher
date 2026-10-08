package se.stilla.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.stilla.launcher.AppEntry
import se.stilla.launcher.LauncherState
import se.stilla.launcher.R
import se.stilla.launcher.ui.common.AppRow
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType
import kotlin.math.abs

private sealed interface ListRow {
    val id: String

    data class Header(val text: String) : ListRow {
        override val id = "h:$text"
    }

    data class App(val entry: AppEntry, val inRecent: Boolean) : ListRow {
        override val id = (if (inRecent) "r:" else "a:") + entry.key
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppListScreen(
    state: LauncherState,
    query: String,
    onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onHome: () -> Unit = {},
) {
    val workSuffix = stringResource(R.string.work_suffix)
    val recentLabel = stringResource(R.string.recently_installed)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // null = no search, show the whole list.
    val results: List<AppEntry>? = remember(query, state) {
        if (query.isBlank()) null else state.search.search(query).mapNotNull { state.byKey[it] }
    }

    val rows: List<ListRow> = remember(state, results, recentLabel) {
        if (results != null) {
            results.map { ListRow.App(it, inRecent = false) }
        } else buildList {
            if (state.recent.isNotEmpty()) {
                add(ListRow.Header(recentLabel))
                state.recent.forEach { add(ListRow.App(it, inRecent = true)) }
                add(ListRow.Header(""))
            }
            state.visible.forEach { add(ListRow.App(it, inRecent = false)) }
        }
    }

    // Where each letter starts in the list, for the A–Z rail.
    val sectionStarts: Map<String, Int> = remember(rows, results) {
        if (results != null) emptyMap() else buildMap {
            rows.forEachIndexed { i, row ->
                if (row is ListRow.App && !row.inRecent && row.entry.section !in this) put(row.entry.section, i)
            }
        }
    }

    // Keyboard opens straight away; scrolling the list by hand puts it away.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    val hideKeyboardOnDrag = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .imePadding(),
    ) {
        SearchField(
            query = query,
            onQuery = onQuery,
            onGo = { results?.firstOrNull()?.let(onLaunch) },
            focusRequester = focusRequester,
        )

        Row(modifier = Modifier.weight(1f).fillMaxWidth().swipeRight(onHome)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxHeight().nestedScroll(hideKeyboardOnDrag),
            ) {
                items(rows, key = { it.id }, contentType = { if (it is ListRow.Header) "h" else "a" }) { row ->
                    when (row) {
                        is ListRow.Header -> SectionHeader(row.text)
                        is ListRow.App -> AppRow(
                            entry = row.entry,
                            workSuffix = workSuffix,
                            onClick = { onLaunch(row.entry) },
                            onLongClick = { onLongPress(row.entry) },
                        )
                    }
                }
                if (results != null && results.isEmpty()) {
                    item(key = "none") {
                        Text(
                            text = stringResource(R.string.no_matches),
                            style = StillaType.Body,
                            modifier = Modifier.padding(horizontal = StillaDimens.Gutter, vertical = 24.dp),
                        )
                    }
                }
                if (results == null) {
                    item(key = "settings") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = StillaDimens.RowHeight)
                                .quietClickable(onSettings)
                                .padding(horizontal = StillaDimens.Gutter),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(text = stringResource(R.string.settings), style = StillaType.Body)
                        }
                    }
                }
            }

            if (results == null && sectionStarts.isNotEmpty()) {
                LetterRail(
                    letters = sectionStarts.keys.toList(),
                    onSelect = { letter ->
                        sectionStarts[letter]?.let { i -> scope.launch { listState.scrollToItem(i) } }
                    },
                )
            }
        }
    }
}

/** Swipe right goes back home: the opposite of the swipe left that opened the list. */
@Composable
private fun Modifier.swipeRight(onSwipe: () -> Unit): Modifier {
    val swipe by rememberUpdatedState(onSwipe)
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val d = change.position - down.position
                // Mostly sideways only, so scrolling the list never sends you home by accident.
                if (d.x > threshold && abs(d.x) > 2 * abs(d.y)) {
                    change.consume()
                    swipe()
                    break
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    onGo: () -> Unit,
    focusRequester: FocusRequester,
) {
    val placeholder = stringResource(R.string.search)
    Column(modifier = Modifier.padding(horizontal = StillaDimens.Gutter).padding(top = 24.dp, bottom = 8.dp)) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            textStyle = StillaType.AppName,
            singleLine = true,
            cursorBrush = SolidColor(StillaColors.Text),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(text = placeholder, style = StillaType.AppName.copy(color = StillaColors.TextQuiet))
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(StillaDimens.Hairline).background(StillaColors.Rule))
    }
}

@Composable
private fun SectionHeader(text: String) {
    if (text.isEmpty()) {
        Spacer(Modifier.height(16.dp))
    } else {
        Text(
            text = text,
            style = StillaType.Body,
            modifier = Modifier.padding(start = StillaDimens.Gutter, top = 16.dp, bottom = 4.dp),
        )
    }
}

/** Letters down the right edge. Touch or drag to jump; a light tick per letter. */
@Composable
private fun LetterRail(letters: List<String>, onSelect: (String) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val currentLetters by rememberUpdatedState(letters)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var active by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(36.dp)
            .pointerInput(Unit) {
                fun pick(y: Float) {
                    val ls = currentLetters
                    if (ls.isEmpty() || size.height == 0) return
                    val i = ((y / size.height) * ls.size).toInt().coerceIn(0, ls.size - 1)
                    val letter = ls[i]
                    if (letter != active) {
                        active = letter
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(letter)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    active = null
                }
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Each letter gets an equal share of the height, so they never overlap,
        // even with the keyboard open.
        letters.forEach { letter ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = letter,
                    style = StillaType.Rail.copy(
                        color = if (letter == active) StillaColors.Text else StillaColors.TextQuiet,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
