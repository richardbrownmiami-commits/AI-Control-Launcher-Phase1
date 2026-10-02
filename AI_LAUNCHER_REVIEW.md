# Launcher implementation handoff

## Baseline and scope

This patch is based on commit `96a4e408bdf81e73619ad5ce470a15137be979e8` (`Connect AI chat to safe theme workflows`). It implements launcher navigation and customization in the existing Android app. It does not push changes or edit `.github/workflows/build-apk.yml`.

## Implemented launcher UX

| Area | Behavior |
|---|---|
| Home pages | Persistent left, main, and right pages; horizontally swipeable; the selected page persists as the next startup page. |
| App drawer | Separate Apps button and overlay; launchable activities in a grid; search; A–Z, Z–A, or package sorting; long-press actions to launch, add a home shortcut, add to dock, or hide from this launcher. |
| Home shortcuts | Page-specific shortcuts; long-press to move between pages, move earlier/later in the grid, launch, add to dock, or remove. Capacity follows configured rows and columns. Ordering is menu-based, not free-form drag-and-drop. |
| Dock | Independent of page shortcuts; persisted shortcut slots and visibility; the app-drawer button remains available when the dock is hidden. |
| Android widgets | Android 11 `AppWidgetHost` picker and provider configuration flow; selected widget is placed on the current page; widget ID/page mapping persists; widgets can be removed. Size is provider/system controlled. |
| Settings | Persisted rows/columns, shortcut icon size, labels, dock slots/visibility, startup page, drawer sort/search/labels, layout preset, active theme, and installed compatible icon pack. |
| Package visibility | Existing `MAIN`/`LAUNCHER` query retained; multiple launchable activities per package are preserved. No `QUERY_ALL_PACKAGES` permission was added. |

## Themes and assistant

The gallery provides nine original built-in palettes: Default, Midnight, Ocean, Ember, Aurora, Sunset, Sage, Paper, and Graphite. Each preset defines colors, card treatment, typography, icon shape, and background treatment. A preview precedes application, and one-step rollback snapshots the previous values—even when a same-named custom preset is replaced. Built-ins are in-app color/style definitions, with no third-party artwork or copied Nova/Lawnchair code.

Custom themes from the existing offline helper or configured AI provider use the validated plan system. The strict allowlist validates typography, icon shape, and background style alongside palette values; explicit confirmation is still required before applying. Existing AI chat, local parser, asset intake, license attribution, and safe confirmation behavior were preserved. Imported wallpapers and Wikimedia Commons results continue to display source/license/creator under the existing reuse policy.

## Compatibility and validation

| Check | Result |
|---|---|
| Android target | `minSdk=30`, `targetSdk=30`, manifest `maxSdkVersion=30`; compile SDK 35; CMake Android API 30. |
| ABI | `armeabi-v7a` only; packaged native library is ELFCLASS32 with `e_machine=40` (32-bit ARM). |
| Tests | **27/27 JVM tests passed.** |
| Lint | `lintDebug` succeeded with **20 warnings and no lint errors**. |
| Builds | `assembleDebug` and `assembleRelease` both succeeded. |
| CI | Workflow left untouched; it still expects SDK 28 / target SDK 35, conflicting with the API-30 requirement. No remote CI pass is claimed. |
| Release/runtime | Release uses the project's debug signing configuration and is not store-ready. No physical-device test is claimed. |

## Remaining limitations

Free-form shortcut dragging/reordering, widget resizing/reordering, user-authored theme palette forms, and physical-device validation remain out of scope for this prototype. Home and drawer share the configurable column count. API-30-only and ARMv7a-only targets are intentional constraints.
