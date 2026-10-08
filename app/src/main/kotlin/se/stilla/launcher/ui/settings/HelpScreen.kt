package se.stilla.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import se.stilla.launcher.R
import se.stilla.launcher.ui.theme.LocalBackground

/** Settings → Help: what each swipe and tap does, so nothing has to be guessed. */
@Composable
fun HelpScreen(foldersOn: Boolean) {
    val sections = listOf(
        R.string.help_home to listOf(
            R.string.help_swipe_left,
            if (foldersOn) R.string.help_swipe_up_folders else R.string.help_swipe_up_apps,
            R.string.help_swipe_down,
            R.string.help_clock,
            R.string.help_date,
            R.string.help_corners,
            R.string.help_music,
        ),
        R.string.help_apps to listOf(
            R.string.help_hold_app,
            R.string.help_drag_favorite,
            R.string.help_settings,
        ),
        R.string.help_folders to listOf(
            R.string.help_folders_open,
            R.string.help_folders_back,
            R.string.help_folders_hold,
        ),
        R.string.help_back_title to listOf(R.string.help_back),
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalBackground.current)
            .systemBarsPadding()
            .padding(bottom = 32.dp),
    ) {
        sections.forEach { (title, lines) ->
            item { Heading(stringResource(title)) }
            items(lines) { SubText(stringResource(it), quiet = false) }
        }
    }
}
