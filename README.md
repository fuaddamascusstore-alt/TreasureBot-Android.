# Treasure Watch / TreasureBot-Android — v0.2

**Android 11+ (API 30+), Kotlin, Gradle, no root, no server, no TikTok API or credentials.**

## Scope and truthfulness
This is a **user-directed local observer** for the standard TikTok Android package `com.zhiliaoapp.musically`.
It reads UI text and accessibility descriptions, recognises candidate treasure boxes (AR/EN/TR and more),
parses plausible countdown text and optionally performs **local cropped-template matching** on the current screen.
A candidate is **not proof that a prize, box or timer is real**. TikTok often renders inaccessible graphics.

The app **does not tap Open/Claim, swipe across TikTok rooms, scroll/search TikTok, bypass CAPTCHAs or collect coins automatically**.
The user opens and navigates LIVE manually. Tap **بث جديد** on the session overlay after *you* move to another stream; this records
that room and resets the per-room detection. The overlay contains **إيقاف فوري**. Leaving TikTok for 90 seconds
stops monitoring. Room, scan and session caps provide automatic stops. The app does not run locked or unattended.

### Data and permissions
- Uses `BIND_ACCESSIBILITY_SERVICE` only, manually enabled in system settings. No INTERNET, SMS, account, storage or overlay permission.
- Android accessibility overlay is scoped to the accessibility service; it appears only during an active session.
- User must affirmatively accept the **on-screen Arabic disclosure** before each monitoring start.
- Only reads UI content from TikTok standard package. No uploads, analytics, screen recording, external servers or passwords.
- User-imported cropped image stays in internal app storage. Live screenshots are processed in memory and discarded.
- Last 120 generic timestamped session events kept locally; reset from Logs. Never stores screen text/usernames.

**Platform caution:** Accessing/rewarding LIVE boxes through unauthorised bots may violate TikTok rules.
A sideload build does not imply approval by TikTok or Google Play. Do not use this build to automate reward collection.

## Phone-only installation through GitHub Actions

1. From your Android browser, create a **separate private GitHub repository** (never the RAWAFID repository).
2. Upload contents of this project ZIP to the root of that repo, preserving `.github/workflows/build-apk.yml`.
   Tip: GitHub web can upload extracted project files via the `Add file > Upload files` option,
   or use an Android Git client. GitHub Actions will only work after source files are committed.
3. In repository **Actions → Android tests and debug APK → Run workflow**; alternatively push to `main`.
4. Only if both `Unit tests` and `Build APK` steps succeed, download the artifact `TreasureWatch-0.2-debug-APK`.
5. Unzip it, compare APK checksum to `SHA256SUMS.txt`, then install `app-debug.apk` on Android 11+.
6. Enable the service in Android accessibility settings and accept the in-app disclosure. Open TikTok LIVE manually.

**Do not disable Play Protect globally.** Android may require allowing APK installs from the chosen trusted file manager/browser.

## Local build for a machine with Android tools

Requires JDK 17, Android SDK Platform 35, Build Tools 35, Gradle 8.11.1 with network access for first dependency resolution:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
test -s app/build/outputs/apk/debug/app-debug.apk
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

APK output: `app/build/outputs/apk/debug/app-debug.apk` only **after successful build**.
This source package intentionally omits a Gradle Wrapper binary; CI installs Gradle explicitly.

## Test coverage and limitations

- `RulesTest.kt`: Arabic/Turkish/English text, time parsing (including Eastern Arabic digits), live context, open label safety.
- `SessionGuardTest.kt`: minimum/maximum bounds, scan expiry, deadline, room cap, deduplication.
- `tools/LocalChecks.kt`: self-contained offline Kotlin assertions; run as:

```bash
kotlinc app/src/main/java/com/treasurewatch/bot/Rules.kt \
  app/src/main/java/com/treasurewatch/bot/SessionGuard.kt \
  tools/LocalChecks.kt -include-runtime -d /tmp/treasure-checks.jar
java -jar /tmp/treasure-checks.jar
```

**Not verified:** APK compilation if Android toolchain/dependencies unavailable; live-device accessibility layout;
TikTok navigation (manual only); countdown reliability; image recognition accuracy; prizes or reward claims.
A live-device test on an Android phone with the real TikTok UI is necessary before stating any of these work.

## Fail-safe details

The observer uses a single main-thread 1-second timer, a separate single-worker image matcher, a five-error
limit, 90-second TikTok inactivity stop, at most one box count per manually registered room,
configurable 5–60 second scan, 1–100 room maximum and a 5–120 minute session cap.
Clicking Stop invalidates stale screenshot callbacks and hides the overlay.

### Alternative: upload using only an Android phone (Termux)

Install Termux from a trusted maintained distribution, and use its terminal (no root and no computer):

```sh
pkg update
pkg install git unzip gh
termux-setup-storage
mkdir -p ~/projects
cd ~/projects
unzip ~/storage/downloads/TreasureBot-Android-updated-source.zip
cd TreasureBot-Android
git init -b main
git config user.name "Android developer"
git config user.email "your-verified-github-email@example.com"
git add . && git commit -m "Initial Treasure Watch v0.2"
gh auth login
gh repo create TreasureBot-Android --private --source=. --remote=origin --push
```

Then launch the GitHub Actions workflow from your phone browser. Replace the placeholder email with your own.
The code does not need to be built locally on the phone; the GitHub runner builds it.
If the `gh` Termux package is unavailable, use a trusted Android Git client instead.
