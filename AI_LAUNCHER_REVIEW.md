# AI Control Launcher — UI overhaul handoff

## Baseline and delivery boundary

This patch is based on `dccb48e60a4178c40afd36c96315d34e5e4bcb22` (`dccb48e`), verified against the repository’s current `main`. Work is on local branch `ui-overhaul-dccb48e`. It has **not** been committed, pushed, or submitted as a pull request. No new UI framework or runtime dependency was added, and the GitHub Actions workflow remains unchanged.

## Visual redesign

The native Kotlin/Android app now uses a quieter, theme-aware launcher palette, restrained typography, grouped surfaces, compact spacing and palette-aware foreground contrast. Home is reorganized around a date/clock, app search, pinned shortcuts/widgets, page indicators, and a persistent dock with an explicit **Apps** entry; separate labelled home controls open **Themes**, **Assets**, **Assistant**, and **Settings**. The three workspace pages remain swipeable, and page indicators now have larger touch areas.

The app drawer, launcher settings, AI provider settings, preset theme gallery, dedicated AI chat, and wallpaper/asset library have been reshaped to reduce oversized form-like headings and dense control walls. Settings are grouped by task; theme cards show miniature launcher/color previews and optional wallpaper art; asset entries show image thumbnails with concise creator/license metadata and separated actions; the AI composer remains multiline and compact. Native system dialogs and controls use a dark-compatible theme, and primary/user-bubble text colors adapt to the active palette.

## Behavior retained

The UI work leaves the existing native feature paths in place: three-page swipe navigation, home shortcuts, app drawer/search/sorting, dock, app menus, installed Android widget picker/hosting, preset and custom theme preview/apply/undo, icon-pack management, persistent assistant history, validated AI plan review/confirm, and wallpaper search/download/preview/apply. Asset attribution, local asset selection, and existing provider/launcher preferences continue through their original stores and managers. No Flutter rewrite was made: Android launcher, widgets, and icon-pack surfaces continue to use the existing native implementation.

## Compatibility constraints

These files were verified unchanged from the baseline: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/cpp/CMakeLists.txt`, and `.github/workflows/build-apk.yml`. The project remains **minSdk 30 / targetSdk 30 / maxSdkVersion 30**, with the **`armeabi-v7a`-only** ABI filter and native API-30 platform target. No Java/API surface above API 30 was intentionally introduced without guards, and no device/emulator run is claimed.

## Verification status for this patch

| Check | Result |
|---|---|
| Baseline | `HEAD` is `dccb48e60a4178c40afd36c96315d34e5e4bcb22`; remote `main` matched when checked. |
| Source whitespace | `git diff --check` passed. |
| Compatibility files | Confirmed unchanged from `dccb48e`. |
| Feature-presence review | Static source markers for navigation, widgets, AI review/confirmation, theme, wallpaper, and icon-pack paths were present. This is not a runtime test. |
| JVM unit tests | `:app:testDebugUnitTest` — **52 passed, 0 failed, 0 errors, 0 skipped**. |
| Android lint | `:app:lintDebug` succeeded — **0 errors, 44 warnings** (38 `SetTextI18n`, 2 `GradleDependency`, 2 `DiscouragedApi`, 1 `UseCompatLoadingForDrawables`, and 1 intentional single-ABI `ChromeOsAbiSupport`). |
| Debug/release assembly | `:app:assembleDebug` and `:app:assembleRelease` both succeeded; fresh APKs were built. |
| Merged APK manifests | Both APKs report `minSdk=30`, `targetSdk=30`, and `maxSdkVersion=30`. |
| Native ABI | Both APKs contain only `lib/armeabi-v7a/liblauncherabi.so`; ELF inspection reports ELF32 / ARM. |
| APK signatures | Both APKs pass `apksigner verify`; each has one signer and verifies with APK Signature Scheme v2. The repository release variant is debug-signed, not production-signed. |
| SDK/license setup | The approved SDK/API 35, Build Tools 35.0.0, NDK 27.0.12077973, and CMake 3.31.6 packages were installed; Google SDK/NDK license prompts were accepted under the user’s authorization. |
| Physical device/emulator | Not tested; no device/emulator claims are made. |

**Patch correction during verification:** The supplied patch initially contained Kotlin compile errors. The final local patch includes small build fixes in `SettingsActivity.kt` (action-row syntax/layout and explicit single-line setter), `MainActivity.kt` (explicit sample-view background setters), and `ThemeGalleryActivity.kt` (the `View` import and explicit view setters). These corrections are included in the baseline-relative delivery patch; no unrelated files or build settings were changed.

`git diff --check` passes. `.github/workflows/build-apk.yml` remains unchanged. No CI result is claimed for this local patch.

## Current upstream Actions result (workflow unchanged)

The latest public run for the baseline `dccb48e` is [run 37120453044](https://github.com/richardbrownmiami-commits/AI-Control-Launcher-Phase1/actions/runs/37120453044), completed with **failure**. Its debug APK build step succeeded, then **Verify debug APK Android 9–15** failed at the existing manifest assertion. The workflow expects `sdkVersion:'28'` and `targetSdkVersion:'35'`, which conflict with the required API-30 manifest; ARM verification, artifact upload, and the release build were consequently skipped. The workflow was not edited. There is no CI result for this local patch because it has not been pushed.

## Distribution notes

Before distribution, configure a private release keystore; repository release signing is not a production-signing claim. This patch does not change the platform/API or ARMv7a restrictions above.
