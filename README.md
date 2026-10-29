# UTC Smart Tap — standalone Android (not RAWAFID)

Android 11+ (API 30+), Kotlin, no root, no backend.

## Modes

1. **Manual UTC reference timer**: user enters countdown (e.g. 40 seconds), touch duration, remaining time and target coordinates. The app launches exactly one gesture at the requested countdown point. The monotonic timer is used for the countdown; UTC is only a wall-clock reference.
2. **Smart TikTok treasure timer (experimental)**: user starts a five-minute accessibility-screen-capture session in TikTok LIVE. The app looks for a top-right gift icon with a visible clock, can tap to open its details, reads the large modal countdown via local bundled ML Kit Latin OCR, and estimates its own remaining time from visual transitions. A checkbox explicitly opts into a single tap when the opening state is confirmed. No auto-scrolling, account credentials, reward claims, network requests or image uploads.

## Important limitations

- TikTok's timer is **not** synchronized to UTC. Displayed countdown seconds do not reveal server-side milliseconds; no reliable optimal tap millisecond can be promised.
- Screenshot rate limits and OCR latency make the visual timer approximate. The app uses the last visible second transition and the *change of the large pink button*, not a guessed UTC timestamp.
- The OCR engine supports Latin/ASCII digits and Arabic-Indic digits *when recognized*; Arabic word recognition is not guaranteed. Accessibility node text provides an additional indication for Open/فتح/Aç, but many TikTok elements are drawn as inaccessible graphics.
- Strict guards: only TikTok app package foreground, visually verified pink button, recent visible 0 or 1 seconds, at least two missing counter reads, user opt-in and one tap per session. If uncertain, **no tap**.
- Auto-open detection uses screen-relative geometry and color checks matching the reference screen supplied by the user; TikTok UI changes and screen layouts can cause false negatives. **Not tested on a physical phone**.
- A request for a 1 ms Android gesture is not equivalent to a guaranteed 1 ms physical touch. Android accessibility operations are asynchronous.
- This application may be restricted by TikTok terms or Google Play accessibility policy; the operator is responsible for appropriate use.
- Android's restricted accessibility settings for sideloaded APKs must be enabled by the user if permitted by the device.
- GitHub Actions debug signing keys are ephemeral; upgrading from v1 may require uninstalling the older APK before installing v2 (which clears local settings).

## Build

JDK 17, Gradle 8.11.1, Android SDK platform/build-tools 35; run `gradle :app:testDebugUnitTest :app:assembleDebug`. Build output: `app/build/outputs/apk/debug/app-debug.apk`. CI attaches the APK and SHA256 checksum.

## Privacy

All screenshot analysis is ephemeral, entirely in memory on-device. No INTERNET permission, no storage of screen content, no analytics. Start and stop are under user control. Overlay Stop button cancels local monitoring and closes OCR.

