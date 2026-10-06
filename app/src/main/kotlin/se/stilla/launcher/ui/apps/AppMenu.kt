package se.stilla.launcher.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.stilla.launcher.AppEntry
import se.stilla.launcher.R
import se.stilla.launcher.ui.common.quietClickable
import se.stilla.launcher.ui.theme.StillaColors
import se.stilla.launcher.ui.theme.StillaDimens
import se.stilla.launcher.ui.theme.StillaType

/** The long-press menu, as a rounded card from the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    entry: AppEntry,
    favoritesFull: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleEssential: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onToggleWatched: () -> Unit,
    onBlock: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var choosingBlock by remember(entry.key) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = StillaDimens.CardRadius, topEnd = StillaDimens.CardRadius),
        containerColor = StillaColors.Surface,
        contentColor = StillaColors.Text,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp)) {
            Text(
                text = entry.label,
                style = StillaType.Body,
                modifier = Modifier.padding(horizontal = StillaDimens.Gutter).padding(bottom = 8.dp),
            )
            if (choosingBlock) {
                Text(
                    text = stringResource(R.string.block_warning),
                    style = StillaType.Body,
                    modifier = Modifier.padding(horizontal = StillaDimens.Gutter).padding(bottom = 8.dp),
                )
                BLOCK_CHOICES.forEach { (label, ms) ->
                    MenuItem(stringResource(label), andThen({ onBlock(ms) }, onDismiss))
                }
            } else {
                if (!entry.essential) {
                    MenuItem(
                        stringResource(if (entry.watched) R.string.menu_unwatch else R.string.menu_watch),
                        andThen(onToggleWatched, onDismiss),
                    )
                    MenuItem(stringResource(R.string.menu_block), { choosingBlock = true })
                }
                when {
                    entry.favorite -> MenuItem(stringResource(R.string.menu_remove_home), andThen(onToggleFavorite, onDismiss))
                    favoritesFull -> MenuItem(stringResource(R.string.menu_home_full), {}, enabled = false)
                    else -> MenuItem(stringResource(R.string.menu_add_home), andThen(onToggleFavorite, onDismiss))
                }
                MenuItem(stringResource(R.string.menu_rename), onRename)
                MenuItem(
                    stringResource(if (entry.hidden) R.string.menu_unhide else R.string.menu_hide),
                    andThen(onToggleHidden, onDismiss),
                )
                when {
                    entry.essentialPermanent -> MenuItem(stringResource(R.string.menu_essential_permanent), {}, enabled = false)
                    entry.essential -> MenuItem(stringResource(R.string.menu_not_essential), andThen(onToggleEssential, onDismiss))
                    else -> MenuItem(stringResource(R.string.menu_make_essential), andThen(onToggleEssential, onDismiss))
                }
                MenuItem(stringResource(R.string.menu_app_info), andThen(onAppInfo, onDismiss))
                if (!entry.raw.isSystem) {
                    MenuItem(stringResource(R.string.menu_uninstall), andThen(onUninstall, onDismiss))
                }
            }
        }
    }
}

private const val HOUR = 60L * 60 * 1000
private val BLOCK_CHOICES = listOf(
    R.string.block_1h to HOUR,
    R.string.block_1d to 24 * HOUR,
    R.string.block_7d to 7 * 24 * HOUR,
    R.string.block_30d to 30 * 24 * HOUR,
)

private fun andThen(first: () -> Unit, second: () -> Unit): () -> Unit = {
    first()
    second()
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (enabled) Modifier.quietClickable(onClick) else Modifier)
            .padding(horizontal = StillaDimens.Gutter),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = StillaType.SettingsRow.copy(
                color = if (enabled) StillaColors.Text else StillaColors.TextQuiet,
            ),
        )
    }
}

@Composable
fun RenameDialog(
    entry: AppEntry,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(entry.key) {
        mutableStateOf(TextFieldValue(entry.label, selection = TextRange(0, entry.label.length)))
    }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StillaColors.Surface,
        shape = RoundedCornerShape(StillaDimens.CardRadius),
        title = { Text(stringResource(R.string.rename_title), color = StillaColors.Text) },
        text = {
            Column {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    textStyle = StillaType.AppName,
                    singleLine = true,
                    cursorBrush = SolidColor(StillaColors.Text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(value.text) }),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(StillaDimens.Hairline).background(StillaColors.Rule))
            }
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text) }) {
                Text(stringResource(R.string.rename_save), color = StillaColors.Text)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) {
                    Text(stringResource(R.string.rename_reset), color = StillaColors.TextQuiet)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = StillaColors.TextQuiet)
                }
            }
        },
    )
}
