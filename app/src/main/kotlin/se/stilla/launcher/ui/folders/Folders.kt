package se.stilla.launcher.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import se.stilla.launcher.AppEntry
import se.stilla.launcher.FolderView
import se.stilla.launcher.R
import se.stilla.launcher.data.StillaPrefs
import se.stilla.launcher.ui.common.AppRow
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType
import kotlin.math.abs

/** A folder starts to feel crowded from this many apps; we say so quietly, never stop you. */
private const val FULL_AT = 8

/** Swipe up on home: your folders, one name per row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoldersScreen(
    folders: List<FolderView>,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onHome: () -> Unit,
    onAllApps: () -> Unit,
    showTips: Boolean = true,
) {
    val list = rememberLazyListState()
    FolderPage(list = list, onHome = onHome, onAllApps = onAllApps) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item { Title(stringResource(R.string.folders_title)) }
            if (folders.isEmpty() && showTips) {
                item { Hint(stringResource(R.string.folders_none)) }
            }
            items(folders, key = { it.id }) { folder ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = StillaDimens.RowHeight)
                        .combinedClickable(
                            onClick = { onOpen(folder.id) },
                            onLongClick = { onEdit(folder.id) },
                            onLongClickLabel = folder.name,
                        )
                        .padding(horizontal = StillaDimens.Gutter),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(folder.name, style = StillaType.AppName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (folders.isNotEmpty() && showTips) {
                item { Hint(stringResource(R.string.folders_hold_hint)) }
            }
        }
    }
}

/** One folder on the whole screen: its name, then its apps. */
@Composable
fun FolderScreen(
    folder: FolderView,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onEdit: () -> Unit,
    onHome: () -> Unit,
    onAllApps: () -> Unit,
    showTips: Boolean = true,
) {
    val list = rememberLazyListState()
    val workSuffix = stringResource(R.string.work_suffix)
    FolderPage(list = list, onHome = onHome, onAllApps = onAllApps) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item {
                Title(folder.name, Modifier.quietClickable(onEdit))
            }
            if (folder.apps.isEmpty() && showTips) {
                item { Hint(stringResource(R.string.folder_empty)) }
            }
            items(folder.apps, key = { it.key }) { entry ->
                AppRow(
                    entry = entry,
                    workSuffix = workSuffix,
                    onClick = { onLaunch(entry) },
                    onLongClick = { onLongPress(entry) },
                )
            }
            if (folder.apps.size >= FULL_AT && showTips) {
                item { Hint(stringResource(R.string.folder_full)) }
            }
        }
    }
}

/**
 * The frame both folder screens share: swipe down (from the top of the list)
 * goes home, and two quiet corners at the bottom, like on home.
 */
@Composable
private fun FolderPage(
    list: LazyListState,
    onHome: () -> Unit,
    onAllApps: () -> Unit,
    content: @Composable () -> Unit,
) {
    val home by rememberUpdatedState(onHome)
    val threshold = with(LocalDensity.current) { StillaDimens.SwipeDistance.toPx() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Only when the list is already at the top, so scrolling back up still works.
                    if (list.canScrollBackward) return@awaitEachGesture
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val d = change.position - down.position
                        if (d.y > threshold && abs(d.y) > abs(d.x)) {
                            change.consume()
                            home()
                            break
                        }
                    }
                }
            }
            .systemBarsPadding(),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
        Row(
            modifier = Modifier.fillMaxWidth().height(StillaDimens.RowHeight),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.folders_home),
                style = StillaType.Corner,
                modifier = Modifier.quietClickable(onHome).padding(horizontal = StillaDimens.Gutter, vertical = 16.dp),
            )
            Text(
                text = stringResource(R.string.folders_all_apps),
                style = StillaType.Corner,
                modifier = Modifier.quietClickable(onAllApps).padding(horizontal = StillaDimens.Gutter, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = StillaType.Body,
        modifier = modifier.padding(start = StillaDimens.Gutter, end = StillaDimens.Gutter, top = 48.dp, bottom = 16.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = StillaType.Body,
        modifier = Modifier.padding(horizontal = StillaDimens.Gutter, vertical = 16.dp),
    )
}

/** "Add to folder": tap a folder to put the app in it (or take it out), or type a new name. */
@Composable
fun FolderPickerDialog(
    entry: AppEntry,
    folders: List<FolderView>,
    onToggle: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember(entry.key) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StillaColors.Surface,
        shape = RoundedCornerShape(StillaDimens.CardRadius),
        title = { Text(stringResource(R.string.folder_picker_title, entry.label), color = StillaColors.Text) },
        text = {
            Column {
                folders.forEach { folder ->
                    val inIt = folder.apps.any { it.key == entry.key }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .quietClickable { onToggle(folder.id) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = (if (inIt) "✓  " else "    ") + folder.name,
                            style = StillaType.SettingsRow.copy(color = if (inIt) StillaColors.Text else StillaColors.TextQuiet),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                NameField(
                    value = newName,
                    hint = stringResource(R.string.folder_new_hint),
                    onChange = { newName = it },
                )
            }
        },
        confirmButton = {
            if (newName.isBlank()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_done), color = StillaColors.Text) }
            } else {
                TextButton(onClick = { onCreate(newName); newName = "" }) {
                    Text(stringResource(R.string.folder_create), color = StillaColors.Text)
                }
            }
        },
    )
}

/** Hold a folder: rename it or delete it (its apps stay in the app list). */
@Composable
fun FolderEditDialog(
    folder: FolderView,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StillaColors.Surface,
        shape = RoundedCornerShape(StillaDimens.CardRadius),
        title = { Text(stringResource(R.string.folder_edit_title), color = StillaColors.Text) },
        text = {
            Column {
                NameField(value = name, hint = folder.name, onChange = { name = it })
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.folder_delete_info), style = StillaType.Body)
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }) {
                Text(stringResource(R.string.rename_save), color = StillaColors.Text)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.folder_delete), color = StillaColors.TextQuiet)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = StillaColors.TextQuiet)
                }
            }
        },
    )
}

@Composable
private fun NameField(value: String, hint: String, onChange: (String) -> Unit) {
    Column {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(StillaPrefs.MAX_FOLDER_NAME)) },
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
                        Text(hint, style = StillaType.AppName.copy(color = StillaColors.TextQuiet))
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(StillaDimens.Hairline).background(StillaColors.Rule))
    }
}
