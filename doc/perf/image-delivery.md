# Placement-aware image delivery proposal

**Implemented in step 10:** [actual variants, encoded savings, device decoding/visual checks and limitations](step-10-startup-images.md). The [artwork manifest and exporter instructions](../../frontend/android/design/artwork/README.md) describe the shipped pipeline. The original proposal and source-size evidence below are retained for context; proposed tiers are not the final generated inventory.

## What the files show

The current 12 PNGs occupy **16,615,336 bytes (15.85 MiB)** in the source tree. Eleven are approximately 1.57 megapixels each; the small Google logo is 200 × 204. Decoding each image once at four bytes per pixel would total **66.15 MiB**, before texture copies, caches or decoder overhead. This is an illustrative total across files, not the measured simultaneous footprint of the app. APK compression/resource processing can change packaged byte counts.

| Asset | Source pixels | Source KiB | RGBA-equivalent MiB |
| --- | --- | ---: | ---: |
| diya_cutout.png | 1254 × 1254 | 792.9 | 6.00 |
| family_hands.png | 1024 × 1536 | 1907.5 | 6.00 |
| gayatri_practice_book.png | 1254 × 1254 | 1534.9 | 6.00 |
| google_g.png | 200 × 204 | 32.9 | 0.16 |
| jasmine_sprig.png | 1024 × 1536 | 1480.3 | 6.00 |
| lotus_emblem.png | 1254 × 1254 | 971.4 | 6.00 |
| lotus_morning.png | 1536 × 1024 | 1800.4 | 6.00 |
| messages_morning_notes.png | 1536 × 1024 | 2034.2 | 6.00 |
| purashcharana_mala.png | 1254 × 1254 | 515.1 | 6.00 |
| ritual_still_life.png | 1536 × 1024 | 1873.6 | 6.00 |
| river_dawn.png | 1536 × 1024 | 1980.8 | 6.00 |
| sandhya_vessel.png | 1374 × 1145 | 1301.9 | 6.00 |

PNG inspection found **248,299 bytes** of chunks outside image/header/palette/transparency data, approximately **1.49%** of total source bytes. Most are `caBX` provenance payloads; the Google logo also has text, EXIF, physical-resolution and color-description chunks. This count includes chunk framing and color information: it is an upper bound on ancillary bytes, not a promise that all can be discarded without color normalization.

Metadata removal helps, but the substantial wins should come from fewer decoded pixels, asynchronous decoding and more compact encoding. Changing PNG to WebP without changing pixel dimensions reduces encoded bytes but does not inherently reduce decoded bitmap memory. Android's [bitmap optimization guidance](https://developer.android.com/develop/ui/compose/graphics/images/optimization) discusses matching resolution to use and choosing suitable formats.

## Actual placements and proposed variants

Android `dp` is a logical size. Pixel demand is `ceil(dp × density)`, measured after layout, with extra allowance only for real scaling animations. A 76 dp square at 3× density needs 228 × 228 pixels, not 76 × 76. Its exact RGBA budget is about 0.20 MiB; a 256-square generated variant is 0.25 MiB, versus about 6 MiB for the current square source. That is roughly 24× fewer pixels at the 256-square tier; decoder rounding/cache reuse can change actual allocation.

| Placement and source evidence | Current layout | Proposed generated tiers (pixels) |
| --- | --- | --- |
| [IllustratedCardContent](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/IllustratedCardContent.kt), Sandhya/guide/Purashcharana Home cards | 56 or 76 dp; book itself 56 dp | 128, 192, 256, 320 bounding-square tiers for cutouts; preserve aspect ratio and padding |
| [PracticeGuideArtwork](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/guide/PracticeGuide.kt), sheet | 72 dp | Same book tiers; 256 at 3×, 320 at 4× |
| [SandhyaSettingsEntry](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SandhyaSettingsScreen.kt) | Vessel at 48 dp | 128, 192, 256 tiers shared with cards |
| [Messages list](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesScreen.kt), `MessageListCard` | Cropped 72 dp square | Dedicated square crops at 128, 192, 256, 320; separate from hero images |
| [MessagesHomeEntry](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesScreen.kt) | Full card width, 124 dp high | Wide banner crop at 640/960/1280 widths as required; measure card bounds separately from full-bleed headers |
| [Welcome](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/auth/WelcomeScreen.kt), [empty state](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/EmptyState.kt), former custom splash (removed in step 10) | Emblem at 48, 140, 160 dp; other uses 72/96 dp | 192, 320, 448, 640; keep transparent canvas/petal placement |
| [Recitation](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/recitation/RecitationScreen.kt) | Diya at 104 dp | 192, 320, 448 |
| [Settings](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SettingsScreen.kt) | Jasmine at 22% of available width, 120 dp tall, Fit | Aspect-preserving 256/384/512 long-edge tiers; resolve both bounds |
| [Purashcharana detail](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/purashcharana/PurashcharanaScreen.kt), [mala component](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/purashcharana/components/PurashcharanaMala.kt) | Mala at 208 dp; emblem overlay at 68% | Mala detail 448, 640, 896 plus card tiers; emblem chosen separately |
| [Home practice-entry backgrounds](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt) | Book/mala fit measured card, 8% opacity; responsive height | Select by actual fitted bounds, up to detail tiers if needed; do not assume these are always 76 dp |
| [AmbientArtwork/PageIntro](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/LotusDesign.kt), Home/Welcome/detail headers | Full bleed width; heights 180, 224, 248 or 260 dp | Per-aspect hero crops at widths 640, 960, 1280, 1536, subject to source resolution |
| [GoogleAuthContent](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/auth/GoogleAuthContent.kt) | Google logo at 20 dp | Aspect-preserving 48/64/80/96 tiers; lossless, approved brand appearance |
| [Launcher foreground](../../frontend/android/src/main/res/drawable/lotus_launcher_foreground.xml) | Platform adaptive icon, currently reuses full emblem | Separate density-qualified launcher foreground, preserving existing inset/safe zone |

Tiers are proposed upper bounds to generate selectively, not a requirement to ship every tier for every file. The measured slot, fitting mode and aspect variant determine the choice. Consolidate redundant tiers after measuring total packaged size. Functional navigation icons already use VectorDrawables and should remain vectors.

The photographic assets serve real full-width headers. For example, a 360 × 248 dp header at 3× density needs a 1080 × 744 px crop. Cropping a 1536 × 1024 source to that aspect then resizing is reasonable; replacing every header with a 320 px thumbnail is not. Family hands is a portrait source used in a wide header: its 1024 px source width can already limit crispness at high densities. A pipeline cannot create missing detail by upscaling. Retain the best available crop and record the limitation, or obtain a larger master separately.

## Generation pipeline

Use one build-time/development asset manifest, keyed by artwork identity and usage, rather than hand-maintaining filenames in screens. Suggested locations are `frontend/android/design/artwork/manifest.json`, masters under `frontend/android/design/artwork/masters/`, and generated Android resources under `res/drawable-nodpi/`. These locations are a proposal, not files added by this audit.

1. Move original masters outside Android `res`/`assets`; retain originals and existing provenance documentation for re-export. List each master hash, dimensions, alpha, focal point, approved usage/aspect crops and generated tiers. For example, `river_dawn` has a square message-thumbnail usage and a wide header usage.
2. Decode the master once in the generation tool. Apply orientation, convert to sRGB when needed, and resize with a high-quality filter. Normalize color before discarding color metadata. Do not flatten transparent cutouts or change their whitespace/inset geometry accidentally.
3. For photographs, generate approved focal-point crops at the required aspect ratios. Start WebP trials around quality 85–90, then adjust per image from actual visual comparisons; this setting is not a universal quality guarantee. For porcelain petals, fine book details and alpha cutouts, compare lossless WebP with optimized PNG first. Preserve full alpha precision and avoid lossy palette conversion.
4. Strip EXIF/GPS, text, comments, timestamps, XMP and provenance payloads from shipped derivatives, including current `caBX` chunks. Retain provenance in the source manifest/docs. Keep only information necessary to decode/render normalized pixels and alpha correctly. Reinspect exported files rather than assuming the encoder removed everything.
5. Produce deterministic resource names such as `lotus_morning_hero_960.webp`, `river_dawn_thumb_256.webp` and `sandhya_vessel_card_192.webp`. Generate a Kotlin resource lookup table containing dimensions, usage and crop metadata. Avoid runtime reflection/string `getIdentifier()` lookup.
6. Validate outputs and wire generation/checking into CI. The app ships generated derivatives only. Do not process masters on a user's first launch or generate every possible size on-device.

Version the encoder and settings, and regenerate only when the master or manifest changes. If density-qualified resources are chosen instead of explicit tiers for a usage, let Android choose density and avoid also shipping a duplicate full set of explicit tiers for it. `nodpi` variants are not automatically split by screen density just because density split delivery is enabled in Gradle.

## Runtime selection and loading

Add a common `AppArtwork(art, usage, modifier, contentScale, alignment)` that preserves current decorative accessibility semantics and layout. Migrate both `LotusArtwork` and direct raster `painterResource` sites; replacing only the central helper misses the book, vessel and mala calls.

Use the existing Coil dependency with an app-shared loader and its bounded memory cache. [Coil 2.6.0's AsyncImage implementation](https://raw.githubusercontent.com/coil-kt/coil/2.6.0/coil-compose-base/src/main/java/coil/compose/AsyncImage.kt) executes an asynchronous request and installs a constraints-based size resolver. Constrain the layout before resolving images; never fall back to original-size requests because a parent is unbounded. Leave a fixed-size lightweight placeholder until finite bounds are available.

Selection rules:

- Convert final drawing bounds to physical pixels and include any actual enlargement during animation. Account for `artworkBleed()` expanding header width by both 24 dp gutters; screen/card inner width alone is insufficient.
- For an uncropped source with dimensions `Sw, Sh` and requested bounds `W, H`, Fit uses `s = min(W/Sw, H/Sh)`; Crop uses `s = max(W/Sw, H/Sh)`. Required source-preserving decoded dimensions are approximately `ceil(Sw*s), ceil(Sh*s)`. This explains why a mismatched-aspect Crop may decode many pixels outside the visible area.
- Prefer an approved pre-cropped aspect variant, then choose the smallest tier meeting both required dimensions. If none is large enough, use the largest available derivative and record the coverage gap; do not upscale a file and count that as higher quality. Bound extreme tablet layouts or provide an adequate master.
- Pass finite target dimensions and the correct scale mode to the async loader as well as selecting the encoded tier. Runtime downsampling and smaller encoded resources solve different costs. Decoder sampling granularity may allocate slightly more than the ideal bound.
- Cache identity must include artwork revision/resource, crop/usage and size semantics. Preserve Coil's normal size compatibility checks; never force thumbnail and hero requests to collide with a custom shared key. Use bucketed sizes to avoid one cache entry for every tiny layout change.
- Keep layout/text independent of image completion, and do not crossfade an entire page while navigating. A solid/theme placeholder or genuinely tiny asset is sufficient. Retain `Fit`, `Crop`, alignment and transparency so subjects do not shift.

Bundled artwork needs no CDN. It is read from the installed app, so its load cost is resource IO, decode, upload and composition rather than an HTTP download. The current `ProfileAvatar` already uses Coil `AsyncImage`; preserve that. For future remote uploads, generate server-side thumbnails/hero variants and request the suitable URL. Client-side downsampling alone does not reduce downloaded bytes. Do not invent resizing parameters for arbitrary Google profile URLs without a supported endpoint contract.

## Quality and regression checks

The quality requirement is visual equivalence at the intended on-screen size. Resizing intentionally discards detail beyond the display requirement; lossy WebP is not mathematically identical. Use lossless output when acceptable quality cannot be reached within a lossy budget.

Inspect actual-size screenshots on mdpi, 3× and 4× devices/emulators, with compact/wide layouts, large fonts, and light/dark backgrounds. Compare the current source and generated derivative rendered into the same slot/crop. Check lotus petals, bead edges, small book marks, brass highlights, fine stems, alpha halos and gradients. Confirm launcher crop/safe area separately. Similarity scores can help flag candidates but cannot replace this check.

Add focused future checks for finite size selection, portrait-to-wide cropping, density changes, minimum covering tier, source-resolution limits and absence of original-size fallback. Reject missing generated outputs, unexpected metadata, lost alpha, changed aspect ratio, and masters accidentally packaged into the APK. Keep an explicit reviewed exception for any size/byte budget violation.

Suggested initial encoded budgets: 40 KiB per small thumbnail; 100 KiB per medium/detail cutout; 250 KiB per hero variant. These are review triggers, not measured achievable sizes or strict reasons to sacrifice quality. Track total packaged derivative size as well: many variants can increase APK size even when each is small.

Acceptance: small slots no longer request ~1.57 MP masters; no large raster decode appears on Main during navigation; image loading cannot block text/actions; exported metadata is verified; visual review passes at the supported density/layout matrix. Record actual encoded savings and runtime memory only after generating and testing candidates.

## Reproduce the current inventory

From the repository root:

```powershell
python doc/perf/inspect_images.py
```

The script reads PNG structure and checks chunk CRCs, writes `doc/perf/image-inventory.csv`, and prints aggregate sizes. It never modifies an image and requires no image library. It measures current PNG source files, not WebP candidates, rendered quality or Android decoder timings. Byte units here are binary: KiB = 1,024 bytes; MiB = 1,048,576 bytes.
