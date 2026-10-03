# AI Control Launcher — visual redesign handoff

## Baseline and delivery boundary

This local, uncommitted change is based on `e4db2a4a35c16afbe2f0abb8399b6730e7c19bc4` (`e4db2a4`), confirmed as the repository's `main` head at checkout. Work is on local branch `visual-redesign-e4db2a4`. It has **not** been pushed, and no pull request or CI pass is claimed. The GitHub Actions workflow is unchanged.

## Visual changes

- **Home:** Replaced four always-visible feature shortcuts with a quiet launcher identity and one accessible tools menu for Assistant, Theme gallery, Wallpapers & assets, and Settings. Removed the duplicate home search field; app search lives in the separate drawer. The central page now gives the live date and clock more prominence, while swipeable home pages, shortcuts, page dots, widget entry, wallpaper, and dock remain in their established positions and flows.
- **App drawer:** Kept it as a distinct full-screen surface for installed apps, with its own search, sort control, and long-press actions. Theme/settings links were removed from the drawer so it remains focused on apps.
- **Theme gallery:** Reflowed theme previews into adaptive two-column cards on sufficiently wide screens and single-column cards on narrow or enlarged-font screens. Each card retains palette, wallpaper and launcher-surface samples, plus theme preview/apply and custom-theme asset editing.
- **Settings hub:** Added a horizontally scrollable category jump strip and icon-led, grouped sections for Appearance, Home screen, Dock, App drawer, Icon packs, and Assistant; existing controls, stored preferences, and save behavior remain.
- **Wallpaper/asset library:** Saved wallpapers now appear as larger image-led gallery cards. Creator/license/source metadata and existing launcher-background, device-wallpaper, theme-icon, and deletion actions remain available.

No new framework or runtime dependency was added. The Kotlin/native Android implementation remains in place; no Nova assets or code were copied.

## Feature-preservation review

Source-path checks confirmed existing implementation remains for three-page workspace swipes, `WorkspaceStore` shortcuts, app drawer search/sort, dock, `AppWidgetHost` picker and configuration, AI provider/conversation persistence and plan validation/confirmation, theme preview/apply/rollback, licensed Commons downloads and preview, device-wallpaper confirmation, and compatible installed icon-pack mapping. This is a static feature-presence review, not an end-to-end device test.

## Compatibility constraints

`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/cpp/CMakeLists.txt`, and `.github/workflows/build-apk.yml` are unchanged from `e4db2a4`. Both freshly assembled APK manifests report `minSdk=30`, `targetSdk=30`, and `maxSdkVersion=30`. Each APK contains exactly `lib/armeabi-v7a/liblauncherabi.so`; ELF inspection reports ELF32 / ARM.

## Verification

| Check | Result |
|---|---|
| Baseline | `HEAD` is `e4db2a4a35c16afbe2f0abb8399b6730e7c19bc4`; local branch `visual-redesign-e4db2a4`. |
| Source whitespace | `git diff --check` passed. |
| JVM unit tests | `:app:testDebugUnitTest` — **52 passed, 0 failed, 0 errors, 0 skipped**. |
| Android lint | `:app:lintDebug` succeeded — **0 errors, 44 warnings**: 38 `SetTextI18n`, 2 `GradleDependency`, 2 `DiscouragedApi`, 1 `UseCompatLoadingForDrawables`, and 1 intentional single-ABI `ChromeOsAbiSupport`. |
| Debug/release APK builds | `:app:assembleDebug` and `:app:assembleRelease` succeeded. |
| APK manifest | Debug and release both report min/target/max SDK **30/30/30**. |
| Native ABI | Both APKs contain only `armeabi-v7a`; extracted library is ELF32 / ARM. |
| APK signatures | Both APKs pass `apksigner verify`; each has one signer and verifies using APK Signature Scheme v2. The release variant uses the repository's debug signing configuration, not a production keystore. |
| Workflow/CI | `.github/workflows/build-apk.yml` was not edited. No CI pass is claimed. |
| Physical device/emulator | Not tested; no device/emulator claim is made. |

The build log also contains the expected 32-bit-only native-library advisory and existing CMake deprecation warnings. The ABI remains ARMv7-only as explicitly required.

## Fresh APK hashes

- Debug: `39139d699e64329460bea4514e989504bbf87d0583a13d46e16758d4a6375c79`
- Release: `a4bf190c740297345f92633b3cf870090c27848be1cfc400b6bc39c700892311`
