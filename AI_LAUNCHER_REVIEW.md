# AI Control Launcher — implementation handoff

## Baseline and scope

This change is based on `70b917c0886533f13d37d76640fec7268c478c88` (`main`, supplied baseline). It is a local baseline-relative patch; it has not been pushed. The committed GitHub Actions workflow was not edited.

## Implemented

- **Home/workspace:** launcher-style live clock/date card, app-library entry, assistant shortcut, dock and fixed app-drawer control. The three left/main/right pages are switched by horizontal swipes with animated page transitions and accessible dot indicators; the selected page persists.
- **Dedicated assistant:** separate chat activity with locally persisted thread, recent-prompt recall, provider/offline status, inline errors, and preview/confirm cards. OpenRouter receives role-separated conversation turns; Gemini history remains role-structured. Existing provider/model/key storage is preserved.
- **Offline helper and attachments:** provider absence is explained in the chat screen while the deterministic offline theme helper remains available. Android's Storage Access Framework selects a local image/file; the assistant does not read or upload selected contents.
- **AI parsing and safety:** response parsing tolerates common fenced/prose-wrapped and stringified JSON without widening the validated action allowlist. Benign supported theme and wallpaper intents are explicitly in-scope. Unsupported actions remain rejected; no model output is executed as code or passed to a generic action runner.
- **Licensed wallpaper workflow:** Wikimedia Commons reuse filtering is retained. Results show creator, license, and source; downloads require a user confirmation, use HTTPS, restrict licensed-image redirects to Wikimedia's upload host, validate MIME/size/decoded image, and expose progress and cancellation. A visual preview precedes a separate confirmation to set the launcher background. Device wallpaper remains a distinct, explicitly confirmed operation and does not implicitly set the launcher background.

## Verification

| Check | Result |
|---|---|
| JVM unit tests | **30 passed, 0 failed, 0 skipped** (`:app:testDebugUnitTest`) |
| Android lint | `:app:lintDebug` succeeded; **0 errors, 33 warnings** |
| APKs | `:app:assembleDebug` and `:app:assembleRelease` succeeded |
| Manifest | Both APKs inspected: `minSdk=30`, `targetSdk=30`, `maxSdkVersion=30` |
| Native ABI | Both APKs contain only `lib/armeabi-v7a/liblauncherabi.so`; inspected ELF is 32-bit ARM (`ELF32`, `Machine: ARM`) |
| CI workflow | Unchanged; no remote CI result is claimed |

## Remaining limitations

- No physical-device or emulator runtime validation was performed.
- The release APK uses the repository's debug signing configuration; it is not store-ready.
- Selected chat attachments are deliberately local-only and are not analyzed or sent to the provider.
- The app remains intentionally restricted to Android API 30 and 32-bit ARMv7a; newer Android versions and 64-bit-only devices are excluded by the requested constraints.
- The existing GitHub Actions SDK/target mismatch remains untouched as instructed.
