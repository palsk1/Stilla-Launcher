package se.stilla.launcher.ui.preview

import android.content.ComponentName
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import se.stilla.engine.Alphabet
import se.stilla.engine.AppId
import se.stilla.engine.AppSearch
import se.stilla.engine.BlockReason
import se.stilla.engine.EngineEvent
import se.stilla.engine.OnTimeOver
import se.stilla.engine.SearchItem
import se.stilla.engine.Verdict
import se.stilla.launcher.AppEntry
import se.stilla.launcher.LauncherState
import se.stilla.launcher.data.AppKey
import se.stilla.launcher.data.RawApp
import se.stilla.launcher.data.RulesState
import se.stilla.launcher.data.ThemeChoice
import se.stilla.launcher.guard.Overlay
import se.stilla.launcher.ui.apps.AppListScreen
import se.stilla.launcher.ui.guard.BlockedCard
import se.stilla.launcher.ui.guard.PromptCard
import se.stilla.launcher.ui.guard.TimeUpCard
import se.stilla.launcher.ui.home.HomeScreen
import se.stilla.launcher.setup.SetupStatus
import se.stilla.launcher.ui.settings.SettingsScreen
import se.stilla.launcher.ui.setup.SetupScreen
import se.stilla.launcher.ui.theme.StillaTheme
import java.time.LocalDateTime
import java.time.ZoneId

/*
 * Every screen drawn with sample apps, so it can be checked in Android Studio
 * without a phone: open this file and pick Split or Design (top right).
 * Size is a Samsung Galaxy S-series phone.
 */

private const val PHONE = "spec:width=384dp,height=854dp,dpi=450"

private val sampleNames = listOf(
    "1177", "BankID", "Chrome", "Gmail", "Instagram", "Kalender", "Kamera", "Klocka", "Kartor",
    "Meddelanden", "Messenger", "Netflix", "Reddit", "Skatteverket", "Spotify", "SVT Play",
    "Swish", "Telefon", "Väder", "Vårdguiden", "WhatsApp", "YouTube", "Östgötatrafiken",
)
private val watchedNames = setOf("Instagram", "Netflix", "Reddit", "SVT Play", "YouTube")
private val favoriteNames = listOf("Telefon", "Meddelanden", "Kalender", "Spotify", "BankID")

private fun sampleState(hidden: Set<String> = emptySet()): LauncherState {
    val user = Process.myUserHandle()
    val entries = sampleNames.map { name ->
        val pkg = "app.sample." + name.lowercase().filter { it.isLetterOrDigit() }
        val key = AppKey(pkg, "$pkg.Main", 0)
        AppEntry(
            key = key.encode(),
            raw = RawApp(key, ComponentName(pkg, "$pkg.Main"), user, name, firstInstall = if (name == "Reddit") System.currentTimeMillis() else 0L, isWork = false, isSystem = false),
            label = name,
            section = Alphabet.sectionOf(name),
            hidden = name in hidden,
            favorite = name in favoriteNames,
            essential = name in setOf("Telefon", "Meddelanden", "BankID", "Kalender", "Klocka"),
            essentialPermanent = name == "Telefon",
            appId = AppId(pkg, 0),
            watched = name in watchedNames,
        )
    }.sortedWith(compareBy<AppEntry> { Alphabet.orderOf(it.section) }.thenBy { it.label })
    val byKey = entries.associateBy { it.key }
    val visible = entries.filter { !it.hidden }
    return LauncherState(
        all = entries,
        visible = visible,
        favorites = favoriteNames.mapNotNull { n -> entries.firstOrNull { it.label == n } },
        recent = visible.filter { it.label == "Reddit" },
        byKey = byKey,
        search = AppSearch(entries.map { SearchItem(it.key, it.label, it.hidden) }),
        rules = RulesState(),
    )
}

private val noop: () -> Unit = {}
private val youtube = AppId("com.google.android.youtube")

// ---- Home ----

@Preview(name = "Home", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomePreview() = StillaTheme {
    HomeScreen(
        favorites = sampleState().favorites, isDefaultHome = true,
        onOpenApps = noop, onLaunch = {}, onLongPress = {}, onPhone = noop, onCamera = noop,
        onClock = noop, onDate = noop, onSetDefault = noop,
    )
}

@Preview(name = "Home · setup unfinished", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeWinsPreview() = StillaTheme {
    HomeScreen(
        favorites = sampleState().favorites, isDefaultHome = true,
        onOpenApps = noop, onLaunch = {}, onLongPress = {}, onPhone = noop, onCamera = noop,
        onClock = noop, onDate = noop, onSetDefault = noop,
        watcherPaused = true,
    )
}

@Preview(name = "Home · first run, sv", device = PHONE, locale = "sv", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeFirstRunPreview() = StillaTheme {
    HomeScreen(
        favorites = emptyList(), isDefaultHome = false,
        onOpenApps = noop, onLaunch = {}, onLongPress = {}, onPhone = noop, onCamera = noop,
        onClock = noop, onDate = noop, onSetDefault = noop,
    )
}

@Preview(name = "Home · dark grey, XL text", device = PHONE, showBackground = true)
@Composable
fun HomeGreyXlPreview() = StillaTheme(theme = ThemeChoice.DARK_GREY, textScale = 1.3f) {
    HomeScreen(
        favorites = sampleState().favorites, isDefaultHome = true,
        onOpenApps = noop, onLaunch = {}, onLongPress = {}, onPhone = noop, onCamera = noop,
        onClock = noop, onDate = noop, onSetDefault = noop,
    )
}

// ---- App list ----

@Preview(name = "App list", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AppListPreview() = StillaTheme {
    AppListScreen(sampleState(), query = "", onQuery = {}, onLaunch = {}, onLongPress = {}, onSettings = noop)
}

@Preview(name = "App list · search \"ka\", sv", device = PHONE, locale = "sv", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AppListSearchPreview() = StillaTheme {
    AppListScreen(sampleState(), query = "ka", onQuery = {}, onLaunch = {}, onLongPress = {}, onSettings = noop)
}

// ---- Mindful cards ----

@Preview(name = "Prompt", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PromptPreview() = StillaTheme {
    PromptCard(
        Overlay.Prompt(youtube, "YouTube", Verdict.Prompt(waitSec = 0, opensLeft = 3, minutesLeftToday = 25)),
        onChoose = {}, onNotNow = noop,
    )
}

@Preview(name = "Prompt · breathing pause, sv", device = PHONE, locale = "sv", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PromptWaitingPreview() = StillaTheme {
    PromptCard(
        Overlay.Prompt(youtube, "YouTube", Verdict.Prompt(waitSec = 10, opensLeft = null, minutesLeftToday = null, softSchedule = "Natt")),
        onChoose = {}, onNotNow = noop,
    )
}

@Preview(name = "Blocked · schedule", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun BlockedPreview() = StillaTheme {
    val until = LocalDateTime.now().plusHours(8).withMinute(0).atZone(ZoneId.systemDefault()).toInstant()
    BlockedCard(
        Overlay.Blocked(youtube, "YouTube", Verdict.Blocked(BlockReason.SCHEDULE, until, "Night", step = 3)),
        onBackHome = noop,
    )
}

@Preview(name = "Time's up", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TimeUpPreview() = StillaTheme {
    TimeUpCard(
        Overlay.TimeUp(youtube, "YouTube", EngineEvent.TimeUp(youtube, OnTimeOver.EXTEND_MINDFULLY, extensionWaitSec = 0, minutesToday = 34)),
        onExtend = noop, onDone = noop,
    )
}

// ---- Settings ----

@Preview(name = "Settings", device = PHONE, showBackground = true, backgroundColor = 0xFF000000, heightDp = 1600)
@Composable
fun SettingsPreview() = StillaTheme {
    SettingsScreen(
        state = sampleState(hidden = setOf("Netflix")),
        isDefaultHome = true, versionName = "0.2.0",
        onSetDefault = noop, onUnhide = {},
        theme = ThemeChoice.BLACK, textScale = 1f, onTheme = {}, onTextScale = {},
        watcherOn = false, onOpenWatcherSettings = noop, onTimeOver = {},
    )
}

// ---- Setup checklist ----

@Preview(name = "Setup · first run", device = PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SetupPreview() = StillaTheme {
    SetupScreen(
        status = SetupStatus(isHome = true, overlay = true, usage = false, batteryFree = false),
        onHome = noop, onOverlay = noop, onUsage = noop, onBattery = noop, onMedia = noop, onFinish = noop,
    )
}

@Preview(name = "Setup · all set, sv", device = PHONE, locale = "sv", showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SetupDonePreview() = StillaTheme {
    SetupScreen(
        status = SetupStatus(),
        onHome = noop, onOverlay = noop, onUsage = noop, onBattery = noop, onMedia = noop, onFinish = noop,
    )
}
