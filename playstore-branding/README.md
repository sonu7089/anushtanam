# Anushtanam Play Store branding pack

Open **preview.html** for the complete gallery, or **preview.png** for a single overview.

## Upload files

| Asset | File | Size |
| --- | --- | --- |
| App icon | `upload/app-icon-512.png` | 512 × 512, RGBA PNG |
| Feature graphic | `upload/feature-graphic-1024x500.png` | 1024 × 500, RGB PNG |
| Six phone screenshots | `upload/phone-screenshots/01-…` through `06-…` | 1080 × 1920, RGB PNG |
| Listing text | `listing/app-title.txt`, `short-description.txt`, `full-description.txt` | Plain English |

**play-store-upload.zip** contains the upload images and listing text. Unzip it before selecting files in Play Console. No video is included.

Upload the screenshots in their numbered order. The first three introduce daily practice, mental japa and personal goals. The next three show Sandhya settings, notes and dark mode. Image descriptions for accessibility are in **listing-copy.md**.

The images are prepared locally. Nothing has been uploaded to Play Console, and the app's launcher resources have not been changed.

## Visual direction

The pack uses the app's actual lotus artwork, Newsreader headings and DM Sans body text. Colours come from the app's botanical green and soft rose palette, with a warm paper background for the store layout.

| Colour | Hex | Use |
| --- | --- | --- |
| Forest | `#263D30` | Dark layouts and headings |
| Sage | `#E4EDE6` | Quiet background panels |
| Lotus rose | `#F2E5ED` | Icon background and mental japa panel |
| Plum | `#795469` | Small accents |
| Warm paper | `#F6F3ED` | Light store layouts |

The icon matches the current Android lotus launcher, including its inset. The older red store icon was not reused. Upload the square export as supplied; the rounded icon in the preview illustrates the store treatment only.

**brand/** also contains a transparent lotus mark, a 1024px icon and light/dark wordmarks. The wordmarks are supplementary brand assets, not replacements for the required square icon.

## Screenshot sources

These are genuine saved Android design-review captures from `docs/ui-review`, with demonstration names, counts and dates. They are not newly captured from a connected phone. The original UI text and values have not been painted over or generated. Layouts scale the captures and clip the system bars inside a simple frame.

Only screens unrelated to the recent Anushtaanam and vykari wording edits are included. The old Practice and Family screenshots were left out because their text or controls differ from the current app. The six chosen screens were checked against the repository's current UI and resources. Before a later release with UI changes, replace the source captures and rebuild this pack.

The exact source of every screenshot and artwork is recorded in **source-provenance.json**. The original files are copied into **source/assets/** so the editable HTML layouts can be opened on their own.

## Copy and naming

The published app name remains **Anushtanam**. The description uses **Anushtaanam** when explaining the practice, as requested. The tone is warm and direct, with simple English and no em dashes. It does not promise spiritual results or rank the app against other apps.

## Rebuild

The editable layouts and renderer are in **source/**. Rendering uses the existing app assets without generating or altering the app interface.

Use Node.js with `playwright` and `sharp` available, and installed Chrome (or set `BRANDING_BROWSER=msedge`). Run from the repository root:

```powershell
node playstore-branding/source/render.cjs
node playstore-branding/source/prepare-listing.cjs
Compress-Archive -Path playstore-branding/upload,playstore-branding/listing -DestinationPath playstore-branding/play-store-upload.zip -Force
```

If the packages are installed outside the usual Node search path, set `BRANDING_NODE_MODULES` to their `node_modules` directory first. The renderer checks dimensions, alpha channels and file sizes and writes **validation.json**. The listing helper checks character limits and writes **listing/validation.json**.

## Google Play specifications checked

Checked on 24 September 2026 against [Google Play preview asset guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) and [store listing guidance](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en).

- Icon: 512 × 512 PNG, under 1024 KB.
- Feature graphic: 1024 × 500 PNG without alpha.
- Phone screenshots: six 1080 × 1920 PNGs without alpha, in a 9:16 ratio.
- Title: at most 30 characters. Short description: at most 80. Full description: at most 4000.
- No price claims, ranking badges, download claims or third-party device branding.

Tablet and TV screenshots are not included because there are no corresponding captures in this pack.
