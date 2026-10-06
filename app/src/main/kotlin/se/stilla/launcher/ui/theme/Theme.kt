package se.stilla.launcher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import se.stilla.launcher.data.ThemeChoice
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One theme's colors. Every theme keeps the same calm, text-only layout; only these change. */
@Immutable
data class StillaPalette(
    val background: Color,
    val text: Color,
    val textQuiet: Color,
    val surface: Color,
    val outline: Color,
    /** Light page: the phone's own bar icons turn dark. */
    val isLight: Boolean = false,
)

private val Black = StillaPalette(
    background = Color(0xFF000000),
    text = Color(0xFFFFFFFF),
    textQuiet = Color(0xFF9E9E9E),
    surface = Color(0xFF1C1C1C),
    outline = Color(0xFF5F5F5F),
)

/** For people who find true black harsh. */
private val DarkGrey = Black.copy(background = Color(0xFF121212))

/** Brushed aluminium: light cool grey with graphite text. */
private val Silver = StillaPalette(
    background = Color(0xFFC9CDD2),
    text = Color(0xFF1C1F23),
    textQuiet = Color(0xFF4E545B),
    surface = Color(0xFFD9DCE0),
    outline = Color(0xFF80868D),
    isLight = true,
)

/** Silver turned down for the evening: dark slate with silver text. */
private val Graphite = StillaPalette(
    background = Color(0xFF26292D),
    text = Color(0xFFDADDE1),
    textQuiet = Color(0xFF8E949B),
    surface = Color(0xFF33373C),
    outline = Color(0xFF61676E),
)

/** Warm off-white, like an e-ink reader. */
private val Paper = StillaPalette(
    background = Color(0xFFEEE9E0),
    text = Color(0xFF2B2824),
    textQuiet = Color(0xFF6B655C),
    surface = Color(0xFFF6F2EB),
    outline = Color(0xFF9E978C),
    isLight = true,
)

fun ThemeChoice.palette(): StillaPalette = when (this) {
    ThemeChoice.BLACK -> Black
    ThemeChoice.DARK_GREY -> DarkGrey
    ThemeChoice.SILVER -> Silver
    ThemeChoice.GRAPHITE -> Graphite
    ThemeChoice.PAPER -> Paper
}

val LocalPalette = staticCompositionLocalOf { Black }

/** The design tokens from the build plan, taken from the chosen theme. */
object StillaColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val Text: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
    val TextQuiet: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textQuiet
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.outline
    val Rule: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.text
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

/** The page background of the chosen theme. */
val LocalBackground = staticCompositionLocalOf { Black.background }

@Composable
fun StillaTheme(
    theme: ThemeChoice = ThemeChoice.BLACK,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val palette = theme.palette()
    SystemBars(palette)
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalBackground provides palette.background,
        LocalTextScale provides textScale,
    ) {
        StillaMaterial(palette, content)
    }
}

/**
 * Paints the window in the theme's background (no black flash behind a light theme)
 * and turns the phone's own bar icons dark on a light theme, so they stay visible.
 */
@Composable
private fun SystemBars(palette: StillaPalette) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.setBackgroundDrawable(ColorDrawable(palette.background.toArgb()))
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = palette.isLight
            isAppearanceLightNavigationBars = palette.isLight
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun StillaMaterial(palette: StillaPalette, content: @Composable () -> Unit) {
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.text,
            onPrimary = palette.background,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.background,
            onSurface = palette.text,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textQuiet,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surface,
            surfaceContainerLow = palette.surface,
            outline = palette.outline,
            outlineVariant = palette.outline,
        )
    } else {
        darkColorScheme(
            primary = palette.text,
            onPrimary = palette.background,
            background = palette.background,
            onBackground = palette.text,
            surface = palette.background,
            onSurface = palette.text,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textQuiet,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surface,
            surfaceContainerLow = palette.surface,
            outline = palette.outline,
            outlineVariant = palette.outline,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
