# Open-license theme assets

Theme generation uses individually licensed image files, not entire repositories with mixed or unclear image rights. The launcher never downloads or installs executable icon-pack APKs.

## Wallpapers — Wikimedia Commons

The packaged 960px JPEG previews are copied into the app's private `launcher-assets/wallpapers` directory on first access. Each file is CC0 1.0; its creator and Commons file page are also stored in `asset-attributions.json` and shown in theme previews.

| App resource | Commons file / title | Creator | License | Original file |
|---|---|---|---|---|
| `commons_abstract_blue.jpg` | [Abstract modern painting, contrasting colour shapes, no. 5.088](https://commons.wikimedia.org/wiki/File:Abstract_modern_painting_art_in_contrasting_color_shapes_on_canvas_with_oil_paint_-_title,_%27no._5.088%27_-_painted_in_1999_by_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.jpg) | Fons Heijnsbroek | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.en) | [Wikimedia upload](https://upload.wikimedia.org/wikipedia/commons/2/20/Abstract_modern_painting_art_in_contrasting_color_shapes_on_canvas_with_oil_paint_-_title%2C_%27no._5.088%27_-_painted_in_1999_by_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.jpg) |
| `commons_abstract_warm.jpg` | [Abstract modern collage, orange and blue, no. 5.097](https://commons.wikimedia.org/wiki/File:Abstract_modern_collage_art_in_strong_contrasting_shapes_in_orange_and_blue_colors_-_on_canvas_and_oil_paint_-_title,_%27no._5.097%27_-_painted_in_1999_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.tif) | Fons Heijnsbroek | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.en) | [Wikimedia upload](https://upload.wikimedia.org/wikipedia/commons/b/bb/Abstract_modern_collage_art_in_strong_contrasting_shapes_in_orange_and_blue_colors_-_on_canvas_and_oil_paint_-_title%2C_%27no._5.097%27_-_painted_in_1999_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.tif) |
| `commons_abstract_sage.jpg` | [Abstract modern collage, contrasting colour shapes, no. 5.103](https://commons.wikimedia.org/wiki/File:Abstract_modern_collage_art_in_composition_of_contrasting_color_shapes,_created_with_oil_paint_on_canvas._Title_%27no._5.103%27_-_painted_in_2000_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_CC0_in_high_resolution_TIFF.tif) | Fons Heijnsbroek | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.en) | [Wikimedia upload](https://upload.wikimedia.org/wikipedia/commons/2/28/Abstract_modern_collage_art_in_composition_of_contrasting_color_shapes%2C_created_with_oil_paint_on_canvas._Title_%27no._5.103%27_-_painted_in_2000_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_CC0_in_high_resolution_TIFF.tif) |

For generated themes, the app searches the [Wikimedia Commons API](https://commons.wikimedia.org/w/api.php) first. It filters each file's reuse license, media type, source host/path, dimensions, title, and image URL, then saves the image and its file-specific attribution before a preview can be offered. If Commons is offline or no suitable result is returned, an included CC0 wallpaper is used and the preview identifies that fallback. Repository-level wallpaper collections with mixed or unclear image rights are intentionally not downloaded automatically.

## Real Android app icon packs — Appstract

Appstract is a separately installed app icon pack, not imagery bundled into this launcher. The official F-Droid listing identifies package `dev.appstract.iconpack`, lists nearly 590 icons, and describes Nova, Lawnchair, ADW, Apex, and Action compatibility. The source is [github.com/yangchoo/Appstract](https://github.com/yangchoo/Appstract); the pack is licensed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). Its [official F-Droid page](https://f-droid.org/en/packages/dev.appstract.iconpack/) is exposed inside the app.

The launcher discovers only compatible packs installed on the device, stores the chosen package identity with a theme, parses that package's standard `appfilter.xml`, and loads mapped icon drawables from its installed resources. If no compatible pack is present, the user is told that apps keep their original icons and may choose to install Appstract through F-Droid. Returning to the theme gallery refreshes installed packages and opens the explicit chooser. The launcher does not fetch APKs or install them.

The source manifest advertises Nova (`com.novalauncher.THEME`), ADW (`org.adw.launcher.THEMES` and `org.adw.ActivityStarter.THEMES`), Apex (`android.intent.action.MAIN` with category `com.anddoes.launcher.THEME`), and Lawnchair (`ch.deletescape.lawnchair.ICONPACK` with category `ch.deletescape.lawnchair.PICK_ICON`) filters. Its `appfilter.xml` uses `<item component="ComponentInfo{package/activity}" drawable="resource_name"/>`; the launcher resolves an exact activity before a package-level mapping.

## Optional decorative illustrations — OpenMoji

The app bundles original 72×72 PNGs from the official [OpenMoji repository](https://github.com/hfg-gmuend/openmoji). OpenMoji graphics are available under [Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/). The in-app source dialog/theme preview labels these as decorative symbol illustrations, **not application icons and not an Android icon pack**, and shows glyph-level source/creator attribution.

Examples of bundled artwork include phone (`1F4F1`), camera (`1F4F7`), settings (`2699`), music (`1F3B5`), and maps (`1F5FA`). They are displayed separately from app icons supplied by Android packages. No app-label matching or app-grid override uses OpenMoji artwork.

## Visible source choices

The app's **Asset sources & licenses** action links to Wikimedia Commons, the Commons API, Appstract's official F-Droid details page, the Appstract source repository/license, and the OpenMoji repository/license. It explains the user-controlled installation requirement for Android icon-pack APKs.
