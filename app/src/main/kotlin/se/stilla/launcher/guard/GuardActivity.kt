package se.stilla.launcher.guard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import se.stilla.launcher.MainActivity
import se.stilla.launcher.StillaApp
import se.stilla.launcher.ui.guard.BlockedCard
import se.stilla.launcher.ui.guard.PromptCard
import se.stilla.launcher.ui.guard.TimeUpCard
import se.stilla.launcher.ui.theme.LocalBackground
import se.stilla.launcher.ui.theme.StillaTheme

/**
 * The "how long?", blocked and time's-up cards, as their own small screen on
 * top of whatever app you're in. It closes itself as soon as the card is
 * answered.
 *
 * Tested in the emulator: drawing these cards inside the home screen and
 * bringing home to the front left a stale copy of the card on screen after
 * Done, in a second window that no longer listened. A separate screen that
 * is opened fresh and finished afterwards avoids that entirely.
 */
class GuardActivity : ComponentActivity() {

    private val container get() = (application as StillaApp).container
    private val guard get() = container.guard

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by container.prefs.state.collectAsState()
            val overlay by guard.overlay.collectAsState()

            // Answered (or cleared): close this screen.
            LaunchedEffect(overlay) { if (overlay == null) finish() }

            // Back means "Not now" on the prompt and "Done" on the others; both go home.
            BackHandler {
                guard.dismissOverlay()
                goHome()
            }

            StillaTheme(theme = prefs.theme, textScale = prefs.textScale) {
                Box(modifier = Modifier.fillMaxSize().background(LocalBackground.current)) {
                    when (val o = overlay) {
                        is Overlay.Prompt -> PromptCard(
                            o,
                            onChoose = guard::choose,
                            onNotNow = { guard.notNow(); goHome() },
                        )
                        is Overlay.Blocked -> BlockedCard(o, onBackHome = { guard.done(); goHome() })
                        is Overlay.TimeUp -> TimeUpCard(
                            o,
                            onExtend = { guard.extend(5) },
                            onDone = { guard.done(); goHome() },
                        )
                        null -> Unit
                    }
                }
            }
        }
    }

    /** You walked away from the card (Home, Recents): forget it, without counting a win. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            guard.dismissIfShowing()
            if (guard.overlay.value == null) finish()
        }
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
