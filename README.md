# AI Control Launcher

An Android HOME launcher targeted strictly at Android 11 / API 30 and 32-bit ARMv7a. Gradle sets `minSdk=30`, `targetSdk=30`, the manifest declares `maxSdkVersion=30`, and native packaging is restricted to `armeabi-v7a`.

## Current features

- **Conversational assistant:** when an OpenRouter or Gemini provider and its key are configured in AI Settings, the prompt field sends the request plus a short recent conversation and appearance/theme context to that selected provider. The model response is shown, and only validated theme, layout/style, Wikimedia search, or Android widget-picker plans are allowed. Clarifications are returned as questions; plans are previewed and must be confirmed before changes or search. Each Commons result still requires its own download confirmation. With no active key, the deterministic offline parser remains available.
- **Wallpapers and image assets:** users can open Android's document picker and select an image from Downloads (or another document source); the selected file is validated and copied into app-private storage. The launcher can search Wikimedia Commons for reusable JPEG/PNG/WebP wallpaper on request. Only CC0, public-domain, CC BY, and CC BY-SA results pass the reuse filter; results show creator, license, and source before download, and attribution is retained in the private asset library. Unknown, NC, and ND licenses are rejected. Search requires network access and sends the entered search phrase to Wikimedia Commons; it uses no paid service.
- **Wallpaper application:** applying an image as the launcher's background does not change Android's device wallpaper. The separate device-wallpaper option has an explicit confirmation.
- **Widgets:** the HOME screen hosts installed Android app widgets using the platform `AppWidgetHost`; users pick/configure providers through Android's widget picker and can remove them from the launcher. A downloaded static icon/wallpaper pack does not provide an Android widget.
- **Customization:** built-in and custom themes, grid layout, card style, labels, dock slots, compatible installed ADW/Nova-style icon packs, and local asset browsing. Custom themes also appear in the settings picker. Hide/show affects this launcher only.
- **Private asset storage:** imported/downloaded assets are kept beneath `filesDir/launcher-assets/`. Images have MIME, size, and decoded-dimension checks. No third-party APK is installed or modified.

## Privacy and limitations

- The app source contains provider settings but no API credentials. Select/configure one of the existing providers and one of its listed models in AI Settings to enable chat. If an installed copy already has a saved key it can use it; this repository/sandbox cannot inspect an Android device's private preferences. No new provider, paid plan, or API key was added.
- In AI mode, only the prompt, recent chat turns, and launcher appearance/available theme names are sent to the selected provider. The app does not send installed-app inventory, local asset names, file contents, or Downloads content. A Commons search phrase is sent to Wikimedia only after the user confirms that plan. A user-selected local image stays on-device unless the user explicitly chooses an upload in a separate flow; this app does not offer such an upload.
- Model output is treated as untrusted JSON and decoded into a strict typed allowlist. URLs, arbitrary downloads, deletion operations, scripts/code, unlisted fields, and unsupported actions are rejected. No model output is executed or passed to the generic action engine.
- The offline parser remains intentionally bounded and is not a conversational model. Network/provider errors offer an explicit path to retry locally; no changes are made automatically.
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

APK files are written to `app/build/outputs/apk/`. The committed GitHub Actions workflow still verifies `sdkVersion:'28'` and `targetSdkVersion:'35'`, which conflicts with this API-30-only target. It was not changed because CI write permission was unavailable previously. The observed Actions run [37052477394](https://github.com/richardbrownmiami-commits/AI-Control-Launcher-Phase1/actions/runs/37052477394) for baseline commit `15de624d` completed with **failure** at `Verify debug APK Android 9-15`; the workflow's SDK/target checks disagree with the API-30-only app. This local verification does not represent a CI run for these unpushed edits.

The prototype's `release` build currently uses the debug signing key. It is useful for build validation but is not a Play Store distribution artifact; configure a private release keystore before publishing.
