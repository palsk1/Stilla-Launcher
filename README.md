# Stilla

A calm, text-only Android launcher. Black screen, white text, no icons,
no internet permission. Kotlin + Jetpack Compose, package `se.stilla.launcher`,
"Stilla Launcher" on Google Play.

The full plan is in the "Stilla Launcher — Build Plan" doc. `HANDOVER.md` has
the decisions behind the current design, the Play setup and what's still
unverified; read it first. This file says what the app does today, how the
code is put together and how to build it.

## Where we are

Version **0.2.0** is live on Play's internal testing track. The launcher
shell, the mindful core, the setup checklist and music controls are in code.
Some of it hasn't been checked on the phone yet (see "Not yet verified" in
`HANDOVER.md`).

### Launcher

- **Home**: clock inside a thin ring, Swedish date, up to 7 favorites, Phone
  and Camera in the bottom corners. Tap the clock for alarms, the date for the
  calendar.
- **Gestures**: swipe up or left anywhere for the app list, swipe down for
  notifications. The status bar is hidden on Stilla's screens (Settings →
  Display → Status bar to show it).
- **Favorites**: hold one and drag to reorder; hold and let go for its menu.
- **Music**: while something plays (or was just paused), the song and three
  thin controls (previous, play/pause, next) sit under the clock. Title and
  artist need the optional Notification access; without it Stilla still
  sends media keys.
- **App list**: search line on top (keyboard opens by itself, Enter opens the
  top match, å/ä/ö-insensitive, one typo allowed), recently installed, A–Z with
  a letter rail on the right (Å Ä Ö after Z, light tick per letter).
- **Long-press menu**: ask how long first / stop asking, block…, add to /
  remove from home, rename, hide, essential on/off, app info, uninstall.
- Hidden apps only show up when you type their full name. Work profile apps
  get a small "work" suffix.
- **Settings** (bottom of the app list): setup, home app, mindful controls,
  display (black or dark grey, text size S/M/L/XL, status bar), hidden apps,
  essentials, about.
- Stilla is a home app (HOME intent filter, "set as default" via RoleManager).

### Setup checklist

Opens by itself on first run; later from Settings → Setup, or the small
"Finish setup" line on home when something is missing. Steps:

1. Make Stilla the home app.
2. Display over other apps (so the cards can appear over a watched app).
3. Usage access (so Stilla can see which app is in front).
4. Battery → Unrestricted (so Android doesn't stop Stilla in the background).
5. Music on home (optional, Notification access).

Each step opens the right system page and comes back by itself once the
switch is on. The checklist also explains Android's "restricted settings"
lock for apps installed from a file (installing from Play avoids it).

### Mindful core

- **Only time-wasters ask "how long?"**: social media and games (as filed on
  Google Play) plus a known list (YouTube, Instagram, Reddit and Reddit
  clients, TikTok, Netflix, SVT Play…) are picked automatically. Bank, email,
  chat (WhatsApp, Messenger, Signal…), maps and the essentials never ask.
  Long-press any app → *Ask how long first* / *Stop asking* to change it. The
  full list is in Settings → Mindful controls. (`engine/…/Distractions.kt`)
- **The prompt**: "How much time do you want to spend on YouTube now?" with
  5, 10, 15 min or your own number, or "Not now". Only time spent *in* the
  app counts.
- **When time is over** (Settings → Mindful controls), one choice for all
  watched apps: *Wait, then +5* (a card counts down 30 s, then 60 s, 120 s for
  each extension that day), *Exit and block* (home, then a 15-minute break),
  or *Only remind* (a buzz every 5 minutes).
- **Block…** for 1 hour, 1 day, 7 days or 30 days. On purpose, a block can't
  be lifted early yet (the cooldown that makes that safe comes in v1).
- **App watcher**: `guard/ForegroundPoller.kt` reads Android's usage history
  (`ACTIVITY_RESUMED` events) every 500 ms while the screen is on and
  unlocked. It catches watched apps opened from notifications, Recents or
  other apps, and sends you home when time is up. It only learns which app is
  in front, never what's on screen. It needs Usage access, plus Display over
  other apps to open the cards from the background. Without it, Stilla only
  asks when you open apps from its own list.
- There is **no accessibility service**: banking apps such as Nordea refuse
  to run while a sideloaded one is on. That also rules out double-tap to lock
  and per-website blocking inside a browser.
- Screen off pauses the clock and never ends a session; a short trip to
  another app (under 2 min) keeps your session; the day resets at 04:00.
- Sessions and today's counts survive Stilla being restarted by Android.
- Never interrupts a phone or video call: a time's-up card waits until the
  call ends.
- Pressing Done and reopening right away costs the same wait as "+5 min".

The engine behind all of this (`engine/`) already has the whole decision
order from the plan, including schedules, daily budgets, open limits and the
commitment cooldown with rule lock. 52 tests cover it, including the
daylight-saving change on 25 October and moving the phone's clock. Schedules,
budgets and cooldowns have no screens yet.

## Test checklist

1. Install from Play (internal testing), open Stilla and follow the setup
   checklist. Does every step tick itself off when you come back?
2. Press Home: does the home screen look right? Swipe up, left and down.
3. In the app list, type "kal": does Kalender come first? Press Enter.
4. Long-press an app → Add to home. Rename it. Hide one and find it by name.
   Hold a favorite and drag it to a new place.
5. Open YouTube from the list: prompt? Open your bank or email: no prompt?
   Pick 5 min in YouTube. After 5 minutes in the app: time's-up card?
6. Open a watched app from a notification or Recents: does the prompt
   appear on top of it?
7. Play music: do the song and controls show under the clock?
8. Settings → Display → Dark grey and text size XL.
9. Call yourself or open BankID: never touched?

Anything odd: a screenshot plus one line is perfect.

## Check screens without a phone

`app/src/main/kotlin/se/stilla/launcher/ui/preview/Previews.kt` draws every
screen with sample apps: home (also first run, and dark grey with XL text),
the app list (also searching "ka"), the prompt, the block card, time's up,
settings and the setup checklist. Open the file and pick **Design** (top
right of the editor). After code changes, use Build → Assemble Project, then
the refresh arrow in the preview pane; a "NoSuchMethodError" there just means
it needs that full rebuild.

## Build and install

- AGP 8.13.0, Kotlin 2.2.20, Gradle 8.14.3 on JVM 21, compileSdk/targetSdk 36,
  minSdk 29. Versions live in `gradle/libs.versions.toml`. Android Studio
  offers to upgrade the Android Gradle Plugin; leave it (AGP 9 would also need
  Gradle Play Publisher 4.x).
- Android Studio → **File → Open** → this folder. Let Gradle sync finish.
- The build variant is **release**, signed with the Play upload key from
  `keystore.properties` (gitignored, only on Jan's PC).
- To make an install file: Build → Generate App Bundles or APKs. Use the copy
  under `app/build/outputs/`, never the one under `app/build/intermediates/`
  (Studio marks that one "test only" and phones refuse it).
- The built-in emulator needs Windows Hypervisor Platform. With software
  graphics it can be used with mouse and keyboard, but playing video crashes
  it; that's the emulator, not Stilla.

To go back to Samsung's launcher at any time: Settings → Apps →
Choose default apps → Home app → One UI Home.

## Publish to Play

In Android Studio: Gradle panel → app → Tasks → publishing → double-click
`publishReleaseBundle`. It uploads to the `internal` track and sets the
version code to Play's highest + 1. Update the release notes in
`app/src/main/play/release-notes/sv-SE/internal.txt` first. Config is in
`app/build.gradle.kts` (Gradle Play Publisher 3.13.0); it reads
`play-service-account.json`, which is gitignored and only on Jan's PC.
`HANDOVER.md` has the rest.

## How it's put together

Two modules:

| Module | What it is |
|---|---|
| `engine` | Plain Kotlin, no Android. The decision engine (`DecisionEngine`: the plan's 7-step order, sessions, budgets, open limits, time-over), rules and schedules (`Rules`), tamper-proof time (`Time`), the commitment cooldown and rule lock (`Commitment`), the time-waster list (`Distractions`), search (`AppSearch`), A–Z (`Alphabet`), essentials. Tests in `engine/src/test` run in milliseconds: right-click the `test` folder → Run Tests. |
| `app` | Everything Android: the activities, screens (Jetpack Compose), the app list, the app watcher, music, settings storage. |

Inside `app/src/main/kotlin/se/stilla/launcher`:

| File | Does |
|---|---|
| `MainActivity.kt` | The home activity. Starts other apps and system screens; Home button and leaving Stilla reset it to the home screen. |
| `LauncherViewModel.kt` | Joins the app list with your settings into what the screens show; navigation state. |
| `StillaApp.kt` | Creates the shared objects once (no DI framework). |
| `data/AppRepository.kt` | The app list from `LauncherApps`, every profile, with a saved snapshot for an instant first frame. |
| `data/StillaPrefs.kt` | Favorites, hidden, renames, essentials, display. SharedPreferences for now. |
| `data/RuleStore.kt` | Watched apps, the time-over choice, active blocks. |
| `ui/StillaRoot.kt` | Picks the screen: home, apps, settings or setup. |
| `ui/home/HomeScreen.kt` | Clock ring, date, favorites (drag to reorder), corners, swipes. |
| `ui/home/NowPlaying.kt` | Song and controls under the clock. |
| `ui/apps/AppListScreen.kt` | Search, list, letter rail. |
| `ui/apps/AppMenu.kt` | Long-press sheet and rename dialog. |
| `ui/settings/SettingsScreen.kt` | Setup, home app, mindful controls, display, hidden apps, essentials, about. |
| `ui/setup/SetupScreen.kt` | The setup checklist. |
| `ui/guard/GuardScreens.kt` | The prompt, block and time's-up cards. |
| `ui/theme/Theme.kt` | The design tokens from the plan (colours, sizes, row height), theme and text size. |
| `guard/GuardActivity.kt` | The cards' own small screen (own task affinity), opened on top of the app you're in and closed once answered. |
| `guard/Guard.kt` | Hosts the decision engine: app switches in, prompts and kick-outs out, the session timer, screen off. |
| `guard/ForegroundPoller.kt` | The app watcher: which app is in front, from Usage access. |
| `guard/EngineStore.kt` | Saves open sessions and today's counts so a restart doesn't lose them. |
| `media/MediaWatcher.kt`, `media/MediaListener.kt` | What's playing and the controls; the listener is the optional Notification access. |
| `setup/SetupCheck.kt` | Which setup steps are done. |

`guard/WatcherService.kt` and `res/xml/watcher_service.xml` are leftovers from
the removed accessibility service and can be deleted.

## Rules this code keeps

- No `INTERNET` permission. Check it any time in App info → Permissions.
- No `QUERY_ALL_PACKAGES`; a `<queries>` entry for launcher apps is enough.
- No accessibility service.
- Only the home activity is exported. The notification listener can only be
  bound by Android (BIND_NOTIFICATION_LISTENER_SERVICE) and never reads,
  hides or changes notifications.
- Prompts and blocks only for time-wasting apps, never bank or email.
- The phone, emergency calls and essentials must always work, in every mode.
- Signing keys and Play credentials are never committed.
