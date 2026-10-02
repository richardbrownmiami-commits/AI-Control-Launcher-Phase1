# Launcher implementation review

## Implemented in this change

### Guided theme creation

The existing local parser now accepts concise named-theme requests such as `Make a Spider-Man theme`. The parser validates theme names/colors and uses a deterministic palette hint for names containing “spider”: dark navy background/card, red accent, blue secondary accent, and neon card style. Explicit user-supplied color/style values take precedence. Before saving, the launcher shows an actual swatch/mock launcher preview; **Apply** persists and activates the custom theme. **Apply + find wallpaper** activates that reviewed theme and opens Assets with an abstract, palette-matched search.

No character artwork or logo is synthesized, downloaded, or bundled. This is a color/style adaptation, not a Spider-Man asset pack. The parser remains bounded and offline, not an open-ended generative model.

### Asset discovery and local images

- The user can choose an image from Downloads via Android's system document picker. The chosen file is copied into app-private storage, with JPEG/PNG/WebP MIME checks, a 10 MB cap, dimension validation, and sampled decoding. The app does not enumerate the full Downloads directory automatically.
- An explicit Assets action searches Wikimedia Commons for bitmap wallpapers. Candidates must be HTTPS files hosted by Wikimedia Commons and carry an allowlisted CC0, public-domain, CC BY, or CC BY-SA license. NC/ND and unknown licenses are excluded. The user sees creator, license URL, and source URL before selecting **Download**.
- Downloaded Commons attribution is stored locally and shown in the asset library. The search phrase is sent to Wikimedia Commons only when the user searches; no API key or paid image/model service is used.
- Selecting **Use in launcher** stores the private wallpaper reference for this app only. Changing Android's system wallpaper is a separate, explicitly confirmed action.

This is a conservative automated filter, not a guarantee that every search result's provenance is correct; the user is shown the Commons source/license for review. Character-specific imagery is not sourced.

### Android widgets and customization

The HOME activity now owns an `AppWidgetHost` and delegates provider selection/configuration to Android's widget picker. Selected installed widgets render on the launcher and can be removed. No third-party APK is installed. Static wallpaper/icon packs are not treated as widget providers.

Saved custom themes appear in the settings theme picker. Existing grid, style, icon-label, dock-slot, installed ADW/Nova-compatible icon-pack, asset-library, and app-grid operations remain. Hiding an app only removes it from this launcher's grid.

## Important limitation: AI/chat

OpenRouter/Gemini integration is still not called by the launcher prompt field. The prompt feature is a deterministic offline command helper with explicit previews; it is **not** a general conversational AI model and does not transmit prompts, app inventory, or chat history. The separate AI settings screen remains only a provider connection tester. This keeps the new guided flow at zero AI-service cost and on-device except for an explicitly requested public Commons search.

## Compatibility and release constraints

- Required target retained: `minSdk=30`, `targetSdk=30`, manifest `maxSdkVersion=30`, native ABI `armeabi-v7a` only. `compileSdk=35` remains for toolchain compatibility; CMake uses Android 30.
- No broad package visibility permission was added.
- The committed Actions workflow remains unchanged as requested. It still expects SDK 28 and target SDK 35, so its metadata verification conflicts with the strict API 30 target.
- Local APK metadata, native ABI, tests, lint, debug, and release builds still require a functioning Android SDK/NDK/CMake installation. A successful CI or physical-device run must not be claimed unless separately observed.

## Follow-up items not included

1. General model-backed chat would require explicit per-send disclosure/consent, opt-in remote mode, an enforceable zero-cost model policy, secure credential storage, and complete action validation before applying model output.
2. The scope-preserving Downloads intake is user-directed through the document picker; a permission-based media index scanner is not implemented.
3. Per-icon artist/license attribution for arbitrary remote icon packs, a custom user-editable palette form, multiple home pages/workspaces, and a widget resize/reorder editor remain future work.
4. A test on a physical Android 11 ARMv7a device is needed to verify OEM-specific widget picker behavior; none is claimed here.
