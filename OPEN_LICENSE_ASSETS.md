# Open-license theme assets

Theme generation uses individually licensed image files, not entire repositories with mixed or unclear image rights. The launcher never downloads or installs executable icon-pack APKs.

## Wallpapers — Wikimedia Commons

The packaged 960px JPEG previews are copied into the app's private `launcher-assets/wallpapers` directory on first access. Each file is CC0 1.0; its creator and Commons file page are also stored in `asset-attributions.json` and shown in theme previews.

| App resource | Commons file / title | Creator | License | Original file |
|---|---|---|---|---|
| `commons_abstract_blue.jpg` | [Abstract modern painting, contrasting colour shapes, no. 5.088](https://commons.wikimedia.org/wiki/File:Abstract_modern_painting_art_in_contrasting_color_shapes_on_canvas_with_oil_paint_-_title,_%27no._5.088%27_-_painted_in_1999_by_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.jpg) | Fons Heijnsbroek | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.en) | [Wikimedia upload](https://upload.wikimedia.org/wikipedia/commons/2/20/Abstract_modern_painting_art_in_contrasting_color_shapes_on_canvas_with_oil_paint_-_title%2C_%27no._5.088%27_-_painted_in_1999_by_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.jpg) |
| `commons_abstract_warm.jpg` | [Abstract modern collage, orange and blue, no. 5.097](https://commons.wikimedia.org/wiki/File:Abstract_modern_collage_art_in_strong_contrasting_shapes_in_orange_and_blue_colors_-_on_canvas_and_oil_paint_-_title,_%27no._5.097%27_-_painted_in_1999_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.tif) | Fons Heijnsbroek | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.en) | [Wikimedia upload](https://upload.wikimedia.org/wikipedia/commons/b/bb/Abstract_modern_collage_art_in_strong_contrasting_shapes_in_orange_and_blue_colors_-_on_canvas_and_oil_paint_-_title%2C_%27no._5.097%27_-_painted_in_1999_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.tif) |
| `commons_abstract_sage.jpg` | [Abstract modern collage, contrasting colour shapes, no. 5.103](https://commons.wikimedia.org/wiki/File:Abstract_modern_collage_art_in_composition_of_contrasting_color_shapes,_created_with_oil_paint_on_canvas._Title_%27no._5.103%27_-_painted_in_2000_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_CC0_in_high_resolution_TIFF.tif) | Fons Heijnsbroek | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.en) | [Wikimedia upload](https://upload.wikimedia.org/wikipedia/commons/2/28/Abstract_modern_collage_art_in_composition_of_contrasting_color_shapes%2C_created_with_oil_paint_on_canvas._Title_%27no._5.103%27_-_painted_in_2000_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_CC0_in_high_resolution_TIFF.tif) |

For generated themes, the app still searches the [Wikimedia Commons API](https://commons.wikimedia.org/w/api.php) first. It filters the individual file's reuse license, media type, source host, dimensions, title, and image URL, then saves the image and its file-specific attribution before a preview can be offered. If Commons is offline or no suitable result is returned, the bundled CC0 wallpaper is used and the preview says so. Repository-level wallpaper collections with mixed/unclear image rights are intentionally not downloaded automatically.

## App icons — OpenMoji

The app bundles original 72×72 PNGs from the official [OpenMoji repository](https://github.com/hfg-gmuend/openmoji). OpenMoji graphics are available under [Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)](https://creativecommons.org/licenses/by-sa/4.0/). The app records per-glyph source and license metadata when copying a glyph to its persistent private asset cache and displays OpenMoji attribution in theme previews.

| Unicode code point | Label/category |
|---|---|
| `1F4F1` | phone |
| `1F5E8` | messages |
| `1F4F7` | camera/photos |
| `1F4C5` | calendar |
| `23F0` | clock/alarm |
| `1F4E7` | email |
| `1F310` | browser/internet |
| `1F5FA` | maps |
| `1F464` | contacts |
| `2699` | settings |
| `1F4C1` | files |
| `1F3B5` | music |
| `1F6CD` | store |
| `1F324` | weather |
| `1F9EE` | calculator |
| `1F4DD` | notes |
| `1F578` | abstract web/browser motif |

Each PNG's upstream paths follow `https://github.com/hfg-gmuend/openmoji/blob/master/color/72x72/<CODE>.png`; raw downloads follow `https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/color/72x72/<CODE>.png`. These symbols are mapped by known app-label categories locally; installed-app names are not sent to OpenMoji. The app does not treat OpenMoji artwork as an installable Android icon pack.

## Visible source choices

The app's **Asset sources** action links users to Wikimedia Commons and the OpenMoji repository/license. For third-party icon packs, users can only browse/apply already-installed compatible Nova/ADW packs through Android/package discovery; no unverified APK is downloaded or installed.
