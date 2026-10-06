package se.stilla.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import se.stilla.launcher.data.ThemeChoice
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The design tokens from the build plan. */
object StillaColors {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFFFFFFF)
    val TextQuiet = Color(0xFF9E9E9E)
    val Surface = Color(0xFF1C1C1C)
    val Outline = Color(0xFF5F5F5F)
    val Rule = Color(0xFFFFFFFF)
}

/** Stilla's own text size setting (S/M/L/XL), applied on top of the phone's font size. */
val LocalTextScale = staticCompositionLocalOf { 1f }

/**
 * Text styles from the plan. Sizes are multiplied by [LocalTextScale] evenly:
 * Android 14+ grows large text less than small text, so scaling through the
 * system font scale barely changed the 28 sp app names.
 */
object StillaType {
    private val scale: Float @Composable @ReadOnlyComposable get() = LocalTextScale.current

    val Clock: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.Text, fontSize = 50.sp * scale, fontWeight = FontWeight.Normal)
    val Date: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.Text, fontSize = 15.sp * scale)
    val AppName: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.Text, fontSize = 22.sp * scale)
    val SettingsRow: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.Text, fontSize = 19.sp * scale)
    val Corner: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.Text, fontSize = 16.sp * scale)
    val Body: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.TextQuiet, fontSize = 14.sp * scale)
    val Small: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(color = StillaColors.TextQuiet, fontSize = 12.sp * scale)
    val Rail: TextStyle @Composable @ReadOnlyComposable
        get() = TextStyle(fontSize = 12.sp)
}

object StillaDimens {
    val RowHeight = 56.dp
    val Gutter = 32.dp
    val CardRadius = 32.dp
    val ChipRadius = 24.dp
    val Hairline = 1.dp
    const val FadeMs = 150
}

/** The page background: true black, or dark grey for people who find black harsh. */
val LocalBackground = staticCompositionLocalOf { StillaColors.Background }

private val DarkGrey = Color(0xFF121212)

@Composable
fun StillaTheme(
    theme: ThemeChoice = ThemeChoice.BLACK,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val background = if (theme == ThemeChoice.DARK_GREY) DarkGrey else StillaColors.Background
    CompositionLocalProvider(
        LocalBackground provides background,
        LocalTextScale provides textScale,
    ) {
        StillaMaterial(content)
    }
}

@Composable
private fun StillaMaterial(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = StillaColors.Text,
            onPrimary = StillaColors.Background,
            background = StillaColors.Background,
            onBackground = StillaColors.Text,
            surface = StillaColors.Background,
            onSurface = StillaColors.Text,
            surfaceVariant = StillaColors.Surface,
            onSurfaceVariant = StillaColors.TextQuiet,
            surfaceContainer = StillaColors.Surface,
            surfaceContainerHigh = StillaColors.Surface,
            surfaceContainerLow = StillaColors.Surface,
            outline = StillaColors.Outline,
            outlineVariant = StillaColors.Outline,
        ),
        content = content,
    )
}
