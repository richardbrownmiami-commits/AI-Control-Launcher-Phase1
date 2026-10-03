# AI Control Launcher — implementation handoff

## Baseline and delivery boundary

This implementation is based on repository commit `f22eacedc808fc198e2b9d3ae3b94b5fea30401a` (`f22eace`). The change is a local patch for the coordinator to review/apply. **It has not been committed, pushed, or submitted as a pull request.** The GitHub Actions workflow is unchanged.

## Product work completed

- **Launcher UI:** retained the native launcher home, three swipeable/persistent pages, dock, app drawer, shortcuts, per-app menus, installed Android widgets, settings, and the nine-preset gallery. Custom theme bundle details are rendered in the gallery/preview rather than only showing a palette.
- **Conversational assistant:** preserved Gemini/OpenRouter setup and role-structured conversation history, local thread persistence, typed action validation, inline errors, and review cards. If a model returns a palette-only custom-theme plan, an explicit **Build full theme bundle** button is available as a fallback; users can instead save it as a draft or apply its palette directly.
- **One-pass theme builder:** after the user confirms the AI plan, the app builds a custom theme draft with validated colors, layout/style, wallpaper, and suitable generic app-icon mappings. One final complete-bundle preview shows the image, mappings, and attribution; **Apply complete theme** is separate from building. The automated path has no per-image approval prompts.
- **Wallpaper discovery:** searches the free Wikimedia Commons API with bounded query fallbacks. It enforces the reuse-license policy, safe title restrictions, dimensions/aspect/pixel bounds, exact Commons HTTPS image-host/path allowlisting, MIME/decode validation, 10 MB download cap, cancellable progress, and preserved creator/license/source attribution. A live 2026-10-03 check showed Commons currently returns `thumb.wikimedia.org` URLs; both that host and `upload.wikimedia.org` are allowlisted only for `/wikipedia/commons/` paths. A real Commons thumbnail downloaded successfully as `image/jpeg` (399,991 bytes).
- **App icons:** reads OpenMoji's public catalog and statically matches generic symbols to installed app labels locally. Icon PNGs are downloaded only from the official OpenMoji repository, validated, and retain CC BY-SA 4.0 credits. Theme-specific icon files apply consistently in the home grid, dock, and app drawer. Preview/save/apply remain distinct.
- **Optional local assets:** there is no background Downloads scan. The SAF picker imports up to six explicitly chosen JPEG/PNG/WebP files (10 MB each). Palette/image-shape and filename relevance are evaluated on-device; ambiguous selections remain user-selectable. Local image content or filenames are not uploaded.
- **Installed themes and widgets:** installed Nova/ADW-compatible icon packages are queried and their documented `appfilter.xml` component/resource mappings resolved from package resources. Plans are restricted to installed packs, with official Google Play search as the installation route; no arbitrary APK sideloading or `.novabackup` restoration is claimed. Widgets use installed Android widget providers and remain separate from theme images.

A request such as “make a Spider-Man theme” is represented as an abstract red/blue/web visual theme. This implementation does **not** bundle Spider-Man, Marvel, or other copyrighted character artwork unless a particular asset is genuinely licensed for reuse.

## Verification

| Check | Result |
|---|---|
| JVM unit tests | `:app:testDebugUnitTest` — **51 passed, 0 failed, 0 errors** |
| Android lint | `:app:lintDebug` succeeded — **0 errors, 43 warnings** (37 `SetTextI18n`, 2 `GradleDependency`, 2 dynamic-resource `DiscouragedApi`, 1 `UseCompatLoadingForDrawables`, and 1 intentional single-ABI `ChromeOsAbiSupport`) |
| Debug/release assembly | `:app:assembleDebug` and `:app:assembleRelease` both succeeded |
| Manifest in both APKs | `minSdk=30`, `targetSdk=30`, `maxSdkVersion=30` |
| Native ABI | Both contain only `lib/armeabi-v7a/liblauncherabi.so`; inspected ELF is 32-bit ARM |
| APK signatures | Both APKs pass `apksigner verify` (v2 signature; one signer) |
| Source hygiene | `git diff --check` clean; `.github/workflows/build-apk.yml` unchanged |
| Live public asset smoke test | OpenMoji catalog/PNG and Commons search/image download returned usable responses with license and dimension metadata |

The native-build warning that 32-bit-only apps omit 64-bit support is expected and retained because the requested product constraint is ARMv7a only. Lint warnings are warnings, not ignored errors: English strings are assembled in programmatic UI, installed icon packs require dynamic resource lookup, and dependency/ChromeOS advisories remain.

The latest upstream Actions run checked before patching failed at the existing manifest-verification step because its API 28/35 expectations conflict with the required API-30-only manifest. That workflow is intentionally unchanged; this local patch was not pushed, so no CI result for this patch is claimed.

## Remaining limitations

- No physical-device or emulator runtime test was possible in this sandbox.
- The release APK is signed with the repository's debug signing configuration; configure a private release keystore before distribution.
- Local inspection is opt-in and limited to files the user explicitly selects; the AI provider does not receive local image content.
- The app intentionally excludes Android versions outside API 30 and devices lacking 32-bit ARMv7a support.
