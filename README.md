# UTC Smart Treasure Timer v3 — one unified screen

Independent Android Kotlin application. **Not related to RAWAFID.**

## Unified flow
1. Enable the manually approved Accessibility service.
2. Enter the tap time relative to the visual chest countdown zero: `00:00.000` means zero; `00:00.200` means 200 ms before the estimated zero; `-00:00.100` means 100 ms after. Set the duration of the requested tap (default 10 ms) and optional minimum number of coins.
3. Opt in to automatic opening of the small box icon and optionally to one automatic click. Press the **single** start button.
4. Open TikTok LIVE. Local accessibility screenshots and ML Kit OCR detect the box, read the countdown and start the **same** floating timer automatically. No manual seconds entry is required.
5. A click is only considered after visual/accessibility evidence of a changed, actionable Open button. If the requested millisecond arrives **before** that evidence, the app may act later; it cannot open the chest early. One click maximum. An emergency Stop button is always visible.

## Accuracy and restrictions
- TikTok's visible timer is not synchronized to UTC. No use of wall-clock UTC for box opening.
- ML Kit reads **displayed seconds**, not the app server's private millisecond deadline. Screen capture/OCR + Android gesture injection add latency. The displayed milliseconds are **estimates only**, and there is no guaranteed winning or opening moment.
- The detector depends on the reference interface's geometry/colors and needs **on-device validation**; app UI revisions may break it.
- On ambiguous OCR, missing coin value when a minimum is requested, missing pink button transition or missing TikTok foreground, **do not tap**.
- The Android Accessibility service must be explicitly enabled; sideload restrictions may require user action.
- User session capped at 5 minutes; no auto-scrolling or room search; no TikTok password, API keys, remote data upload, network permission or secret collection.
- This automation may be subject to the third-party app's terms of service and Android Play restrictions.

## Build
JDK 17, Android SDK 35, Gradle 8.11.1.
`gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`
The workflow for `smart/unified-timer-v3-20261029` uploads `UTC-Unified-Smart-Timer-v3-APK`.

## Installation
This version updates `versionCode` to 3. Debug signatures from different CI runs may vary. If upgrading fails with a signature mismatch, uninstall the old debug APK and install v3 again (which clears app preferences).
