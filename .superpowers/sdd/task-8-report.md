# Task 8 Report: Android project + Carbon theme

## Status

Complete on `feat/getmoney-mvp`. Compose Android app scaffold with IBM Carbon
tokens and `GetMoneyTheme` is committed; debug APK builds successfully.

## Commit

- `5d754dd feat(android): Compose app scaffold with Carbon theme`

## Implementation

- Gradle project at `apps/android/` (minSdk 26, targetSdk 35, Compose BOM
  2024.12.01, AGP 8.7.3, Kotlin 2.0.21).
- Carbon color tokens in `Color.kt` per brief (`IbmBlue`, `Ink`, `Canvas`, etc.).
- `GetMoneyTheme` in `CarbonTheme.kt`: Material3 light scheme, 0dp
  `RoundedCornerShape` everywhere, primary button helpers with 0 elevation.
- IBM Plex Sans via Google Fonts downloadable provider (`Type.kt`, weight 300
  on `displayLarge` for the “GetMoney” title).
- `MainActivity`: white canvas, centered “GetMoney” in Plex Light.

## Build verification

- `./gradlew.bat :app:assembleDebug` **BUILD SUCCESSFUL** (35 tasks) using
  `JAVA_HOME=C:\Program Files\Java\jdk-17` (default system JDK 25 is unsupported
  by Gradle 8.11.1).
- APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`
- Emulator/manual UI check not run in this session.

## Concerns

- IBM Plex Sans loads at runtime via Google Play Services downloadable fonts;
  devices without GMS may fall back until bundled fonts are added.
- Build requires JDK 17 (or 21); JDK 25 on PATH fails with `IllegalArgumentException: 25.0.1`.
- `local.properties` is gitignored; developers need `sdk.dir` set locally.

## Review fix (Critical)

- Updated `apps/android/.gitignore`: `**/build/` and `.gradle/` (replacing root-only `/build`).
- Removed ~513 tracked files under `apps/android/app/build/` from the index via `git rm -r --cached`.
- Commit: `chore(android): ignore and untrack app build artifacts`.
