# SpendLens

An Android expense manager that reads bank, card and UPI SMS on your phone and turns them into a monthly dashboard with budgets. No internet permission: nothing ever leaves the phone.

## What's in the box

| Area | What it does |
| --- | --- |
| SMS import | Reads only the SMS inbox (READ_SMS). Keeps bank/card/UPI transactions; drops OTPs, promotions, reminders, failed payments and every message from a person. |
| Import choices | Pick senders, how far back (3/6/12 months), money in/out, a minimum amount and skip words, then untick anything on a review list. Change it later and re-import. |
| Dashboard | Budget ring with pace, over-budget banners, category donut, recent transactions with real app icons (Swiggy, Uber…) when those apps are installed. |
| Budgets | Overall and per-category monthly budgets, pace marker, budget month that can start on your salary day. |
| Categories | 18 defaults; add, rename, recolour, change icon, archive. "Always use" rules learn from your corrections. |
| Delete and reset | Delete one transaction (with undo, and it never comes back), by age, by bank or card, all SMS imports, or reset everything. |
| Security | SQLCipher AES-256 database; key wrapped by Android Keystore (StrongBox when available); optional biometric app lock; screenshot blocking; no backups; no INTERNET. |

## Project layout

- `core/` – pure Kotlin: SMS parser, sender/bank registry, merchant registry, categorizer, de-duplication, import filters, budget maths. Fast JVM tests with 21 sample bank messages.
- `app/` – Android app: Jetpack Compose UI, encrypted database, Keystore, SMS reader, background sync.
  - `src/test` – Robolectric tests: full user journeys on Android 8.0, 9, 11, 13, 14, 15 and 16, six screen sizes, manifest security checks, import pipeline.
  - `src/androidTest` – emulator tests: Keystore + SQLCipher encryption, real SMS inbox reading, the production app end to end.
- `.github/workflows/android.yml` – builds the APKs and runs everything above, plus emulators for API 26, 29, 31, 33, 34 (tablet), 35 and 36.

## Build and test

Requirements: Android Studio (latest stable), which bundles a JDK. On the command line use JDK 21.

```
./gradlew :core:test                 # parser and budget logic
./gradlew :app:testDebugUnitTest     # Robolectric: every Android version, screen sizes
./gradlew :app:assembleDebug         # app-debug.apk
./gradlew :app:connectedDebugAndroidTest   # with a phone or emulator connected
```

Every push to `main` runs `.github/workflows/android.yml` on GitHub Actions:

| Stage | What runs |
| --- | --- |
| Build | Parser and budget tests; debug and release APKs; Robolectric journeys on Android 8, 9, 11, 13, 14, 15 and 16 plus small phone, large phone, tablet, foldable, dark mode and 1.5× font; lint; no INTERNET permission; 16 KB page-size alignment |
| Emulators | Pixel 2 (Android 8), Pixel 3a (10), Pixel 5 (12), Pixel 6 (13, 15, 16), Pixel C tablet (14): encrypted database, Keystore key, real SMS inbox, full app journey and reset |

The APKs are attached to each run as the `spendlens-apks` artifact. Android 17 emulator images don't boot reliably on GitHub's standard runners, so Android 17 is tested on a real phone.

Installing on your phone: see the step-by-step guide shared with this project.

## Versions

AGP 8.13.2 · Gradle 8.14.3 · Kotlin 2.2.20 · Compose BOM 2025.12.00 (Material 3) · compileSdk/targetSdk 36 · minSdk 26 · SQLCipher for Android 4.10.0 · Robolectric 4.16.1.

## Before publishing to Google Play

1. Change `applicationId` in `app/build.gradle.kts` (it can never change after the first upload).
2. Put a real contact email in the in-app privacy policy (`PrivacyScreen`) and publish the same text at a public URL.
3. Create an upload key and set `SPENDLENS_KEYSTORE`, `SPENDLENS_KEYSTORE_PASSWORD`, `SPENDLENS_KEY_ALIAS`, `SPENDLENS_KEY_PASSWORD`, then `./gradlew :app:bundleRelease`.
4. Fill in the SMS Permissions Declaration (SMS-based money management) in Play Console.
