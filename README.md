# AI Control Launcher

An Android HOME launcher targeted strictly at Android 11 / API 30 and 32-bit ARMv7a. Gradle sets `minSdk=30`, `targetSdk=30`, the manifest declares `maxSdkVersion=30`, and native packaging is restricted to `armeabi-v7a`.

## Current features

- **Guided offline theme helper:** the bounded on-device prompt parser understands requests such as `Make a Spider-Man theme`, explicit color prompts, built-in/custom theme selection, grid/card commands, and app hide/show/open. Each recognized change is reviewed before application. A custom palette is shown in an in-dialog visual preview before saving. The Spider-Man-named example selects a red/blue neon palette only; no character logo/art is generated or included.
- **Wallpapers and image assets:** users can open Android's document picker and select an image from Downloads (or another document source); the selected file is validated and copied into app-private storage. The launcher can search Wikimedia Commons for reusable JPEG/PNG/WebP wallpaper on request. Only CC0, public-domain, CC BY, and CC BY-SA results pass the reuse filter; results show creator, license, and source before download, and attribution is retained in the private asset library. Unknown, NC, and ND licenses are rejected. Search requires network access and sends the entered search phrase to Wikimedia Commons; it uses no paid service.
- **Wallpaper application:** applying an image as the launcher's background does not change Android's device wallpaper. The separate device-wallpaper option has an explicit confirmation.
- **Widgets:** the HOME screen hosts installed Android app widgets using the platform `AppWidgetHost`; users pick/configure providers through Android's widget picker and can remove them from the launcher. A downloaded static icon/wallpaper pack does not provide an Android widget.
- **Customization:** built-in and custom themes, grid layout, card style, labels, dock slots, compatible installed ADW/Nova-style icon packs, and local asset browsing. Custom themes also appear in the settings picker. Hide/show affects this launcher only.
- **Private asset storage:** imported/downloaded assets are kept beneath `filesDir/launcher-assets/`. Images have MIME, size, and decoded-dimension checks. No third-party APK is installed or modified.

## What is not implemented

- The prompt helper is **not a general-purpose conversational AI model**. It is a deterministic, bounded offline parser; it sends no prompt, app inventory, or history to OpenRouter/Gemini. The separate AI settings screen remains a connection tester. The guided request → preview → apply/asset-search path is implemented without any model or paid service, but open-ended chat is not.
- Downloads is **not silently scanned wholesale**. Android's scoped storage is respected: the user opens the document picker and selects the relevant file. The launcher does not crawl the Downloads folder without the user's selection.
- Commons search finds abstract, openly reusable imagery, not Spider-Man artwork. License filtering and metadata reduce reuse risk but do not replace a human review of the source page and license terms.
- A static downloaded icon set is not represented as a compatible installed Android widget provider.
- No physical-device installation or runtime test is claimed.

## Android compatibility

- `minSdk=30`; `targetSdk=30`; manifest `maxSdkVersion=30`. `compileSdk=35` is used for build-tool availability; CMake targets Android 30.
- Native build/APK packaging accept `armeabi-v7a` only; validation checks the packaged ELF machine type and rejects other ABIs.
- `maxSdkVersion` expresses intent but is not a universal sideload blocker; Android documentation cautions against relying on it. This target also means the launcher is not available on Android releases below 11.
- Package visibility is limited to launcher and ADW/Nova icon-pack intent queries; the app does not request `QUERY_ALL_PACKAGES`.

## Build and verification

Requires JDK 17+, Android SDK platform 35, Build Tools 35.0.0, NDK 27.0.12077973, CMake 3.31.6, and the committed Gradle 8.9 wrapper.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

APK files are written to `app/build/outputs/apk/`. The repository's current GitHub Actions workflow still verifies `sdkVersion:'28'` and `targetSdkVersion:'35'`, which conflicts with this API-30-only target. That workflow is intentionally left unchanged; its status must not be inferred from local builds.

The prototype's `release` build currently uses the debug signing key. It is useful for build validation but is not a Play Store distribution artifact; configure a private release keystore before publishing.
