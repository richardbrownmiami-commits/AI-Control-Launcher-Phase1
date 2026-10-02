# AI Control Launcher

Android HOME launcher configured to run on Android 11 (API 30) and 32-bit ARMv7a. The minimum SDK is API 28 (Android 9); the APK targets API 35 and contains a native ARMv7a ABI guard library.

## Current features
- A local prompt command bar for creating/applying themes, changing grid layout/card style, hiding/restoring an app in this launcher, and opening an installed app. Parsing runs on-device, requires no API key, and previews every recognized command for confirmation.
- Launcher settings for preset themes, layouts, styles, dock slots, labels, and installed ADW/Nova-compatible icon packs.
- A private asset library at `context.filesDir/launcher-assets/` for downloaded wallpapers, images, icons, icon packs, themes, styles and cache. Assets can be browsed, applied or deleted from the Assets screen.
- An image downloader that enforces HTTPS, image MIME and decoded-image validation, and a 10 MB size limit.
- OpenRouter and Gemini provider code with a JSON action protocol and free/free-tier model lists. The current launcher prompt box does **not** send prompts to an external model; the provider settings screen is a connection tester only.
- API keys are stored in app-private SharedPreferences in this prototype. Android Keystore-backed storage remains future work.

## Local prompt examples
The interpreter deliberately recognizes a bounded set of commands and asks for clarification if an app name is ambiguous. Examples:
- `What can you do?`
- `Create a theme called Ocean with blue background and cyan accent.`
- `Make a midnight theme.`
- `Set layout to dense.`
- `Set card style to neon.`
- `Hide Calculator.`
- `Show Calculator.`
- `Open Camera.`

Theme creation uses the supplied background/accent/card colors (named colors or `#RRGGBB`), with a dark background, cyan accent, purple secondary accent, dark card, and glass style as defaults. A review dialog shows the exact resulting palette before saving. Custom theme names and styles/colors are validated before persistence; an invalid or edited theme file falls back safely rather than being applied.

## Android compatibility
- `minSdk`: 28 (Android 9); installs on Android 11 / API 30.
- `targetSdk`: 35; `compileSdk`: 35. API 30 is supported through the API 28 minimum and source-level compatibility checks.
- Native build and APK packaging are restricted to `armeabi-v7a`; local artifact checks verified the packaged native library is a 32-bit ARM ELF binary. The existing CI checks the APK ABI paths.
- The manifest declares only launcher and ADW/Nova icon-pack intent queries needed for Android 11 package visibility. It does not request broad `QUERY_ALL_PACKAGES` visibility.
- App source was checked for platform APIs introduced after API 30; the reviewed calls are available by the minimum supported API level.

## Build
Requires JDK 17+ and Android SDK platform 35, Build Tools 35.0.0, NDK 27.0.12077973 and CMake 3.31.6. The committed Gradle wrapper pins Gradle 8.9.

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
```

APK outputs are written under `app/build/outputs/apk/`. GitHub Actions builds both variants and verifies SDK metadata and ABI packaging; local validation additionally runs lint/unit tests and inspects the ELF header.

See [AI Launcher review and next steps](AI_LAUNCHER_REVIEW.md) for the current implementation assessment, safe-integration guidance, and prompt behavior examples.
