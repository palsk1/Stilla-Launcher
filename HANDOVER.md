# Stilla — handover (6 Oct 2026)

Read this first, then README.md. Jan writes in Swedish; answer in Swedish.

## What Stilla is

Jan's own minimalist Android launcher (replacement for the paid "minimalist phone" app):
text-only home and app list, plus mindful blocking of time-wasting apps. Kotlin + Jetpack
Compose. Package `se.stilla.launcher`, Play name "Stilla Launcher" (another app on Play is
already called "Stilla: Sleep & Meditation"). Phone: Samsung, Swedish UI, 3-button nav.

## Where things live

- **Project folder on Jan's PC:** `C:\Users\Jan\Desktop\Stilla launcher` (Windows, device
  "desktop-ncrqpmd"). This is the only complete copy: signing key, Play key and builds
  are here and nowhere else.
- **GitHub:** https://github.com/palsk1/Stilla-Launcher (was empty at handover; Jan pushes the
  first commit from GitHub Desktop).
- **Plan doc:** "Stilla Launcher — Build Plan",
  https://claude.ai/code/artifact/a3260df6-68b1-42ed-8bbf-d51cb9e37f3c

### Secrets: never commit, never print (all gitignored)

- `upload-key.jks` + `keystore.properties`: Play **upload** key (alias `stilla-upload`).
  Play App Signing holds the real app key. Jan should keep a backup outside the PC.
- `play-service-account.json`: Google Cloud project `stilla-publish`, service account
  `stilla-uploader@stilla-publish.iam.gserviceaccount.com`. Its Play Console rights: view app
  info and publish to **testing tracks** for Stilla Launcher only. (An unused
  `stilla-uploader-4@…` account exists with no key; it can be deleted.)

## Google Play

- Developer account 6592745478511462302 (personal, name "Mayboar Inc"), app id 4972535741231991741.
- **Internal testing:** version 6 (0.2.0) is live. The tester list "Stilla" contains only
  janmajdzik@gmail.com. Opt-in link: https://play.google.com/apps/internaltest/4700897743884731212
- Installing from Play removes Android's "restricted settings" lock. Sideloaded copies were
  signed with the PC debug key, so Jan must uninstall them once before installing from Play.
- **Publishing a new version:** in Android Studio, Gradle panel → app → Tasks → publishing →
  double-click `publishReleaseBundle`.
  - Uses Gradle Play Publisher **3.13.0** (4.x needs AGP 9); config is in `app/build.gradle.kts`.
  - It goes to the `internal` track, and the version code is set automatically (Play max + 1).
  - Release notes come from `app/src/main/play/release-notes/sv-SE/internal.txt`: update it each time.
  - Not tested end to end yet. Only the credentials were verified (the listing was read back).
- **GitHub Actions** (`.github/workflows/play-internal.yml`): every push to `master` (or
  Actions → "Play internal testing" → Run workflow) runs the engine tests, then
  `publishReleaseBundle` to internal testing, using the same release notes file.
  - The Gradle build reads the upload key and the Play key from env vars only when
    `keystore.properties` / `play-service-account.json` are missing, so the PC build is unchanged.
  - Repo secrets: `STILLA_UPLOAD_KEYSTORE_BASE64`, `STILLA_KEYSTORE_PASSWORD`, `STILLA_KEY_ALIAS`,
    `STILLA_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`. Jan adds them himself; never ask for the values.
  - Version code stays automatic (Play max + 1); uploads run one at a time.
- `bootstrapListing` fails with "Please migrate to the new publishing API". That only concerns
  the old in-app-products endpoint, so ignore it.
- Before any wider release: privacy policy URL, store listing (sv-SE default, add en-US),
  data safety, content rating.

## Build setup

- AGP 8.13.0, Kotlin 2.2.20, Gradle 8.14.3 (Studio must use JVM 21), compileSdk/targetSdk 36, minSdk 29.
- Studio's build variant is set to **release**, signed with the upload key.
  - Use Build → Generate App Bundles or APKs.
  - Never hand Jan the `intermediates/` APK: it's testOnly.
- Studio's "Configure all Gradle tasks during sync" is on, so the publishing tasks show in the Gradle panel.
- The cloud sandbox can't reach Google/Maven, so builds happen on the PC.
  - The engine unit tests (52, pure Kotlin) can run in the sandbox with kotlinc.
- How the last session worked, if no shell on the PC is available:
  - Edit files in the cloud copy, then write them back with device_commit_files. Stage under
    /mnt/user-data/outputs and wait ~30 s before committing.
  - Drive Android Studio by clicks only (computer use, "click" tier, no typing).
  - Watch for unescaped apostrophes in strings.xml: they break `mergeReleaseResources`.

## Architecture (short)

- `:engine` (pure Kotlin): DecisionEngine, its 7-step verdict order, Moment/DayRules (04:00 reset),
  sessions, extension waits, commitment, Distractions list.
  - Schedules, budgets, open limits and cooldowns exist in the engine but have **no UI yet**.
- `:app`:
  - Screens: MainActivity (home role) → StillaRoot → Home / Apps / Settings / Setup screens.
  - Prompt, blocked and time's-up cards live in `guard/GuardActivity` (own task affinity).
  - `guard/Guard.kt` hosts the engine.
- **App watcher:** `guard/ForegroundPoller.kt` reads UsageStats `ACTIVITY_RESUMED` every 500 ms
  while the screen is on.
  - It needs **Usage access**, plus **Display over other apps** so it can open the cards from the background.
  - The old accessibility service was removed on purpose (see decisions).
- `media/MediaWatcher` + `MediaListener`: music controls under the clock.
  - Needs Notification access for title/artist.
  - Without it, falls back to media keys plus `isMusicActive`.
- `setup/SetupCheck` + `ui/setup/SetupScreen`: the checklist.
  - Steps: home app, overlay, usage, battery (App info → Unrestricted), music (optional).
  - Each step opens the right page and comes back by itself once the switch is on.
  - It also explains "restricted settings".

## Jan's decisions (keep them)

- Prompts and blocks only for time-wasting apps (Instagram, YouTube, Reddit…), never bank or email.
- **No accessibility service:** Nordea refused to open while a sideloaded one was on.
  - This means no double-tap-to-lock, and no per-website blocking inside Firefox.
  - For sites, suggest the LeechBlock NG add-on in Firefox instead.
- Minimal and quiet:
  - Text was made ~20% smaller.
  - The "wins today" line was removed.
  - The top banners became one small "Finish setup" line.
  - No calculator in search (Jan rejected it).
- Home gestures:
  - Swipe up or left → app list.
  - Swipe down → notification shade.
  - Status bar hidden on Stilla's screens (setting).
  - Favorites reorder by hold + drag; hold + release opens the menu.
- Priority after testing: **music controls** (done in 0.1.4).
  - Postponed: schedules UI and notification filter.
- Still open: clock ring = battery (minimalist phone does that) or screen time?

## Not yet verified on the phone (0.1.3 → 0.2.0)

- Usage-access watcher and cards appearing over apps.
- Drag-and-drop favorites.
- Swipe-down shade: it uses a hidden StatusBarManager call and falls back to briefly showing the status bar.
- Music controls and auto-return to the checklist.
- **Screenshot bug:** Jan says screenshots (side buttons) were blocked with a message naming
  Stilla. Nothing in the code sets FLAG_SECURE. Ask for the exact text and screen.

## Next ideas (ask Jan before starting)

- From minimalist phone's docs (all 48 Android articles were read):
  - Folders, including in favorites.
  - Pinned shortcuts from other apps.
  - Battery ring.
  - Configurable swipe-up target.
  - A toggle for the auto-opening keyboard.
  - Silencing notifications of blocked apps.
- UI for schedules, budgets and cooldowns.
- AGP 9 upgrade (later; it would need GPP 4.x).

## Housekeeping on the PC

- `Stilla-0.2.0.aab` in the root is a leftover (gitignored).
- A `Stilla-Launcher` subfolder exists inside the project. Probably a clone of the empty repo; check before deleting.
