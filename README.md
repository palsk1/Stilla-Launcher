# Stilla

A calm, text-only Android launcher. Black screen, white text, no icons,
no internet permission. The full plan is in the "Stilla Launcher — Build Plan"
doc; this file says how the code is put together and how to run it.

## Where we are

**Phase 1 (launcher shell)** and the **first slice of Phase 2 (mindful core)**
are in code and build cleanly. Nothing has run on a phone yet.

Launcher shell:

- Setup checklist (first run, Settings → Setup): home app, "Display over
  other apps", "Usage access", battery. Each opens the right page, comes back
  by itself once switched on, and ticks itself off.
- The app watcher uses Usage access (polled twice a second while the screen
  is on), not an accessibility service: banking apps such as Nordea refuse to
  run while a sideloaded accessibility service is on. (0.1.3)
- Home: swipe up or left for apps, down for notifications; status bar hidden.
  Hold a favorite and drag to reorder; hold and let go for its menu.
- Stilla is a home app (HOME intent filter, "set as default" via RoleManager)
- Home: clock inside a thin ring, Swedish date, up to 7 favorites, Phone and
  Camera in the bottom corners. Tap the clock for alarms, the date for the
  calendar. Swipe up anywhere for all apps.
- App list: search line on top (keyboard opens by itself, Enter opens the top
  match, å/ä/ö-insensitive, one typo allowed), recently installed, A–Z with a
  letter rail on the right (Å Ä Ö after Z, light tick per letter)
- Long-press menu: ask how long first, block…, add to / remove from home,
  rename, hide, essential on/off, app info, uninstall
- Hidden apps only show up when you type their full name
- Work profile apps get a small "work" suffix
- Settings (bottom of the app list): home app, mindful controls, display
  (black or dark grey, text size S/M/L/XL), hidden apps, essentials

Mindful core:

- **Only time-wasters ask "how long?"**: social media and games (as filed
  on Google Play) plus a known list (YouTube, Instagram, Reddit and Reddit
  clients, TikTok, Netflix, SVT Play…) are picked automatically. Bank, email,
  chat (WhatsApp, Messenger, Signal…), maps and the essentials never ask.
  Long-press any app → *Ask how long first* / *Stop asking* to change it.
  The full list is in Settings → Mindful controls. (`engine/…/Distractions.kt`)
- **The prompt**: "How much time do you want to spend on YouTube now?" with
  5, 10, 15 min or your own number. "Not now" counts as a win. Only time
  spent *in* the app counts; a short trip elsewhere pauses the clock.
- **When time is over** (Settings → Mindful controls), one choice for all
  watched apps: *Wait, then +5* (a card counts down 30 s, then 60 s, 120 s for
  each extension that day), *Exit and block* (home, then a 15-minute break),
  or *Only remind* (a buzz every 5 minutes).
- **Block…** for 1 hour, 1 day, 7 days or 30 days. On purpose, a block can't be
  lifted early yet (the cooldown that lets you do that safely comes in v1).
- **App watcher** (an accessibility service): catches watched apps opened from
  notifications, Recents or other apps, and sends you home when time is up.
  It only reads which app is in front, never the screen. Without it, Stilla
  only asks when you open apps from its own list.
- Screen off pauses the clock and never ends a session; a short trip to
  another app (under 2 min) keeps your session; the day resets at 04:00.
- Sessions and today's counts survive Stilla being restarted by Android.
- Never interrupts a phone or video call: a time's-up card waits until the
  call ends. Without the app watcher, cards wait until you next open Stilla
  (Stilla can't see what's in front, and it could be BankID).
- Pressing Done and reopening right away costs the same wait as "+5 min".
- **Home**: "3 wins today" under the clock when you've backed out of the
  prompt; "Blocking is paused" at the top if the app watcher is off (tap it to
  fix); double-tap an empty spot to lock the phone (needs the app watcher).
- **Favourites**: Move up / Move down in the long-press menu.

The engine behind all of this (`engine/`) already has the whole decision
order from the plan, including schedules, daily budgets, open limits and the
commitment cooldown with rule lock. 52 tests cover it, including the
daylight-saving change on 25 October and moving the phone's clock. The
screens for schedules, budgets and cooldowns come next.

## Test checklist for the first run

1. Run it, press Home, pick Stilla → Always. Does the home screen look right?
2. Swipe up. Type "kal": does Kalender come first? Press Enter.
3. Long-press an app → Add to home. Rename it. Hide one and find it by name.
4. Open YouTube from the list: prompt? (It's picked automatically.) Open
   your bank or email: no prompt? Pick 5 min in YouTube. After 5 minutes in the app
   (with the watcher on): time's-up card?
5. Settings → Mindful controls → Turn on the app watcher. Android shows a
   warning screen; that's normal for accessibility services. Then open the
   watched app from a notification or Recents: do you land on the prompt?
6. Try Settings → Display → Dark grey and text size XL.
7. Call yourself or open BankID: never touched?
8. Long-press a favourite → Move up. Double-tap an empty spot on home: locks?

Anything odd: a screenshot plus one line is perfect.

## Tested in the emulator (6 Oct 2026)

Worked: set as home app, home screen, wins line (survives a crash), app list
and letter rail with the keyboard open, search ("yout" → YouTube, Enter →
prompt), long-press menu, "Ask how long first", the minutes stepper, the
prompt bouncing in when a watched app is opened from elsewhere, the time's-up
card on time with its countdown, Done, +5 min, the extra wait when reopening
right after time's up, the 04:00 day reset, "Blocking is paused" and its
shortcut to the accessibility settings.

Fixed along the way: the time's-up card ignored Done/Back (cards now have their
own screen), and "Not now" left you in the search.

Good to know:
- **Installing a new build from Android Studio switches the app watcher off.**
  The "Blocking is paused" line says so; tap it and switch it back on.
- The emulator runs in its own window with software graphics (set in Android
  Studio → Settings → Tools → Emulator, and in the emulator's own settings), so
  it can be used with the mouse and keyboard. Playing video (YouTube) crashes it
  that way; that's the emulator, not Stilla.

## Check screens without a phone

`app/src/main/kotlin/se/stilla/launcher/ui/preview/Previews.kt` draws every
screen with sample apps: home (also first run in Swedish, and dark grey with
XL text), the app list (also searching "ka"), the prompt, the block card,
time's up and settings. Open the file and pick **Design** (top right of the
editor). After code changes, use Build → Assemble Project, then the refresh
arrow in the preview pane; a "NoSuchMethodError" there just means it needs
that full rebuild.

## Install from a file (no cable)

Android Studio → Build → Generate App Bundles or APKs → **Generate APKs**.
The file lands in `app/build/outputs/apk/debug/app-debug.apk`. Don't use the
copy under `app/build/intermediates/`: Studio's Run button marks that one
"test only" and phones refuse to install it ("App not installed").
`gradle.properties` now turns that mark off, but the outputs copy is the safe one.

On the phone, open the file, allow installing from that app, then follow
Stilla's setup checklist (it opens by itself the first time; later from
Settings → Setup, or by tapping "Blocking is paused" on home).

## Run it on your phone

1. Android Studio → **File → Open** → pick this folder (`Stilla launcher`).
   Let Gradle sync finish (first time downloads a few hundred MB).
   If it asks to install Android SDK Platform 36, say yes.
2. On the phone: Settings → About phone → Software information → tap
   **Build number** seven times. Then Settings → Developer options →
   **USB debugging** on.
3. Plug the phone in, accept the "Allow USB debugging?" prompt on the phone.
4. Pick your phone in the device dropdown at the top of Android Studio and
   press **Run** (the green triangle).
5. Press the phone's Home button. Android asks which home app to use: pick
   Stilla, **Always**. (Or tap the line at the top of Stilla's home screen.)

To go back to Samsung's launcher at any time: Settings → Apps →
Choose default apps → Home app → One UI Home.

## How it's put together

Two modules:

| Module | What it is |
|---|---|
| `engine` | Plain Kotlin, no Android. The decision engine (`DecisionEngine`: the plan's 7-step order, sessions, budgets, open limits, time-over), rules and schedules (`Rules`), tamper-proof time (`Time`), the commitment cooldown and rule lock (`Commitment`), search (`AppSearch`), A–Z (`Alphabet`), essentials. Tests in `engine/src/test` run in milliseconds: right-click the `test` folder → Run Tests. |
| `app` | Everything Android: the activity, screens (Jetpack Compose), the app list, settings storage. |

Inside `app/src/main/kotlin/se/stilla/launcher`:

| File | Does |
|---|---|
| `MainActivity.kt` | The home activity. Starts other apps and system screens; Home button and leaving Stilla reset it to the home screen. |
| `LauncherViewModel.kt` | Joins the app list with your settings into what the screens show; navigation state. |
| `StillaApp.kt` | Creates the shared objects once (no DI framework). |
| `data/AppRepository.kt` | The app list from `LauncherApps`, every profile, with a saved snapshot for an instant first frame. |
| `data/StillaPrefs.kt` | Favorites, hidden, renames, essentials. SharedPreferences for now; Room arrives in Phase 2. |
| `ui/home/HomeScreen.kt` | Clock ring, date, favorites, corners, swipe up. |
| `ui/apps/AppListScreen.kt` | Search, list, letter rail. |
| `ui/apps/AppMenu.kt` | Long-press sheet and rename dialog. |
| `ui/settings/SettingsScreen.kt` | Home app, hidden apps, essentials, about. |
| `ui/theme/Theme.kt` | The design tokens from the plan (colours, sizes, row height), theme and text size. |
| `ui/guard/GuardScreens.kt` | The prompt, block and time's-up cards. |
| `guard/GuardActivity.kt` | The cards' own small screen, opened on top of the app you're in and closed once answered. |
| `guard/Guard.kt` | Hosts the decision engine: app switches in, prompts and kick-outs out, the session timer, screen off. |
| `guard/WatcherService.kt` | The accessibility service. Package names only; pop-ups and overlays never cause a prompt. |
| `guard/EngineStore.kt` | Saves open sessions and today's counts so a restart doesn't lose them. |
| `data/RuleStore.kt` | Watched apps, the time-over choice, active blocks. |

Versions live in `gradle/libs.versions.toml`. Android Studio offers to
upgrade the Android Gradle Plugin; leave it for now, the current versions
build. Gradle runs on JVM 21 (Android Studio asked once and remembers).
The built-in emulator needs Windows Hypervisor Platform (Windows features →
Windows Hypervisor Platform, then restart).

## Rules this code keeps

- No `INTERNET` permission. Check it any time in App info → Permissions.
- No `QUERY_ALL_PACKAGES`; a `<queries>` entry for launcher apps is enough.
- Only the home activity is exported, plus the watcher service, which only
  Android can bind (BIND_ACCESSIBILITY_SERVICE).
- The phone, emergency calls and essentials must always work, in every mode.
