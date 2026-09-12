# Bundled artwork

Original PNGs and their provenance chunks live in `masters/`, outside Android resources and assets. `manifest.json` records their hashes, dimensions, color policy, crop alignment, placements and encoder versions. `generated.json` records every shipped derivative and the generated Kotlin lookup table by SHA-256. Do not copy masters back into the app as placeholders or fallbacks.

Use Python with **Pillow 12.3.0 / libwebp 1.6.0** from the repository root:

```text
python scripts/generate-artwork.py
python scripts/generate-artwork.py --check
python scripts/review-artwork.py
```

The generator applies EXIF orientation, converts embedded ICC profiles to sRGB, preserves alpha, resizes with Lanczos and removes metadata. Current Google PNG chromaticities/gamma describe sRGB; the other originals have no alternate color profile. Photos use WebP quality 90; cutouts and Google use lossless WebP with exact transparent pixels. Reopened lossless outputs must match the resized RGBA pixels byte for byte. Encoding never upscales a source. Launcher exports preserve the existing 18% inset in the Android XML.

Every Android build runs `:app:verifyArtwork`, checking the manifest, generator, masters, outputs, lookup table and resource inventory. Full regeneration/metadata/pixel checks run with `--check`; CI can use the same pinned Python environment. A manifest/exporter change requires regeneration. `.gitattributes` fixes the line endings of hashed text files.

| Artwork | Actual delivery |
| --- | --- |
| Book, mala | 128–896 px long-edge tiers; shared small cards and larger fitted/detail placements |
| Vessel | 128–320 px; 48–76 dp slots |
| Diya | 128–448 px; up to 104 dp |
| Emblem | 128–640 px; up to 160 dp and the mala overlay |
| Jasmine | 256/384/512 px long edges; both width and height constrain fitting |
| Google | 48/64/80/96 px long edges; 20 dp slot |
| Photos | 640/960/1280/1536 px long edges; original aspect preserved |
| Family hero | Centered vertical square crop, 640/960/1024 px; avoids decoding invisible portrait regions in wide headers |
| Photo thumbnails | Right-aligned square crops at 128/192/256/320 px, matching existing CenterEnd placement |
| Messages banner | Centered vertical 2:1 crop at 640/960/1280/1536 px widths |
| Launcher | Separate 108/162/216/324/432 px density-qualified assets |

`AppArtwork` waits for measured physical-pixel bounds and selects the smallest covering tier with Fit/Crop semantics. Loading uses the app-shared Coil cache and a finite tier-sized request. Text and actions do not wait for image completion. Larger-than-source layouts use the largest derivative; the family source has only 1024 pixels of usable width, and photo masters cannot supply native 2400-pixel-wide tablet detail. See `doc/perf/step-10-startup-images.md` for measurements, visual review, and quality-budget exceptions.
