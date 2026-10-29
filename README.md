# UTC Tap Timer
Standalone native Kotlin Android app. Floating manual countdown, one tap at a specified remaining duration, UTC reference via Android 13+ currentNetworkTimeClock when available. No root and no screen reading.
Build: Gradle 8.11.1 + JDK 17 + Android SDK 35; run `gradle :app:assembleDebug`.
Enable the app's Accessibility service manually, enter seconds and tap delay, open overlay, pick target, start.
1ms is a requested gesture duration; Android cannot guarantee millisecond-precise delivery.
