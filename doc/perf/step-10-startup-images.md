# Step 10 — Startup and sized artwork (12 September 2026)

## Changes

The existing launcher component is now an activity alias for Main. Android's native splash remains; the extra Compose splash, synchronous emblem load, 800 ms animation and 1,000 ms hold are removed. The app's minimum SDK is 33, so no compatibility library is needed. Existing launcher shortcuts retain their component name, and notification intents still enter Main. Longer identity/recovery work remains visible inside the app.

Required account resolution, ownership, Google recovery and child PIN setup checks remain unchanged. Local profile association/session establishment and durable recovery/import scheduling still complete before startup succeeds. The bridge now requests a process-owned Default-dispatcher reminder re-arm after those checks. Application no longer runs a second alarm scheduling pass. Identical startup owners coalesce; replacement and sign-out cancel obsolete work, and the scheduler rechecks the preference owner after suspended log reads. Notification registration and other reminder scheduling owners remain step 11 work.

VoiceModelViewModel owns its initial query. The gate's first resume joins it even when initialization already completed. Later resumes, retry and download completion queue a fresh query behind any in-flight query without cancelling/recreating its recognizers. A result invalidated while in flight is not published. Readiness remains revalidated, and download state plus deliberate unverified-trial safeguards are retained. Benchmark-only counters measure status checks, support queries and startup reminder requests.

## Artwork delivery

All 12 original PNGs moved unchanged into `frontend/android/design/artwork/masters/`. The manifest records hashes, dimensions, crop rules, color handling and encoder versions. Runtime sources contain only generated derivatives: 79 app-artwork resources plus five density-qualified launcher resources. The launcher retains its existing inset/safe area.

The common AppArtwork component replaces every bundled raster painterResource call, including book/vessel/mala/Google sites outside LotusArtwork. It waits for measured physical-pixel bounds, including header bleed, selects the smallest covering Fit/Crop tier, and submits a finite tier-sized Coil request. Layout and actions do not wait for loading; thumbnail/banner/hero resources retain separate cache identities. Vectors and the existing remote avatar loader remain in place.

Photos use quality-90 WebP; alpha artwork and Google use exact lossless WebP. The exporter applies orientation and ICC normalization before dropping metadata, preserves transparent pixels, never upscales masters, and verifies decoded lossless pixels. `--check` reproduces every output byte for byte and rejects metadata/missing/stale outputs. Every Android preBuild also verifies master/output/table/manifest/exporter hashes and rejects PNG masters in drawable-nodpi. Hashed text files have LF checkout rules.

| Encoded artwork | Bytes | MiB |
| --- | ---: | ---: |
| Original 12 PNGs | 16,615,336 | 15.85 |
| All 84 shipped derivatives, including launcher densities | 4,500,370 | 4.29 |
| Reduction | 12,114,966 | 11.55 (72.91%) |

A square 76 dp card selects 128 px at mdpi, 256 px at 3×, and 320 px at 4×: 64/256/400 KiB of RGBA-equivalent pixels instead of approximately 6 MiB. These are decoded pixel dimensions, not a claim about total process RAM. Runtime selection uses the same constraints in tests and production.

The initial 100 KiB cutout budget has six reviewed detail exceptions: book at 448/640/896 px (131.5/267.9/532.9 KiB), emblem at 640 px (177.7 KiB), and mala at 640/896 px (107.4/196.2 KiB). Retaining lossless fine edges/detail is preferable to palette/lossy alpha conversion. All photo hero variants remain below 250 KiB and thumbnails below 40 KiB. Oversized diya, vessel, jasmine and emblem tiers with no corresponding placement were removed.

Family's original usable width is only 1024 px. Its centered vertical square hero crop avoids decoding portrait regions outside wide headers while retaining the existing visible composition. Other photos preserve their original aspect, with separate square thumbnails and a 2:1 Messages banner. Larger-than-source layouts use the largest derivative: high-density wide tablets cannot acquire detail absent from the masters. No master fallback or generated upscaling disguises that limitation.

## Verification

- 618 unit tests: 364 domain, 140 data, 114 app. Coverage includes identity/setup/recovery gates, account replacement during startup, durable recovery before reminder requests, no synchronous reminder wait, initial/resume coalescing, invalidated results, finite image sizing, Fit/Crop, density, bleed and source limits.
- 32 Pixel device tests cover every app derivative's actual bitmap dimensions, off-Main decode and immediate memory-cache reuse; delayed image loading leaves layout/actions usable; changing density selects new tiers. Existing auth, guide, Home, large-font/accessibility and reminder settings/notification tests pass. Three old Home tests were updated to scroll lazy sections before looking up offscreen text.
- 210 source/derivative slot comparisons generated across 1×/3×/4× density and light/dark surfaces. Reviewed every artwork identity, emphasizing petals, book/bead edges, flame, stems, brass, Google colors, thumbnails and banner gradients. Worst mean absolute channel difference is 4.891/255 (mdpi vessel, influenced by integer aspect rounding); this metric supplements visual review.
- 24 real Pixel fixture screenshots cover Welcome, Home cards, Messages, Family, Settings, Purashcharana, Recitation and Guide. Configurations: 360 dp mdpi/light; 360 dp 4×/dark; 600 dp 3×/light with font scale 1.3. Original 1440×3120 / 560 dpi / font scale 0.85 was restored. Higher-density wide photo softness follows the documented master limit. Real signed-in Home and launcher routing were also checked.
- Debug and optimized benchmark builds pass; lint reports no errors. APK inspection verifies all derivative resources and absence of original master hashes. The deterministic exporter and Gradle artwork guard pass.

## Reproduction and evidence

Source: `scripts/generate-artwork.py`, `scripts/review-artwork.py`, `frontend/android/design/artwork/{manifest,generated}.json`, and the adjacent artwork README. PNG inventory now describes the retained masters at their new paths.

Local evidence is under `build/step10/`: `comparisons/`, `screens/`, `unit-results.json` and `packaging.json`. Build/test logs are `step10-release-check.log` and `step10-device-final.log`. Source/derivative comparisons are reproducible with the pinned encoder; Pixel screenshots are rendered fixture evidence, not three separate physical devices. Broad release/two-device recovery verification remains step 13.

## Startup and live work measurements

The optimized final APK passed all five AppBaseline device tests, with 20 measured repetitions per startup/navigation scenario. Raw JSON and 81 Perfetto/benchmark artifacts are retained at `build/perf-baseline/20260912-183452`; [aggregate results](step-10-results.json) include hashes and limits.

| Observed launch to usable | Step-00 median / p95 (ms) | After step 10 median / p95 (ms) |
| --- | ---: | ---: |
| Cold | 4,757.683 / 5,367.152 | 2,747.534 / 3,853.886 |
| Warm | 2,943.151 / 3,072.445 | 293.726 / 350.018 |
| Hot | 86.339 / 95.113 | 91.627 / 114.315 |

Android's fully-drawn median is separately 2,326.194 ms cold and 140.760 ms warm; initial display is 221.257 / 53.448 ms. These are different metrics from the table's explicit launch-to-usable observer. Navigation medians are Home 262.832 ms, Progress 295.709 ms and Settings 286.868 ms. Idle CPU is 0.333% of one core; navigation frame-overrun p95 is 6.117 ms (7.481% positive), so this is not a claim that all rendering jank is resolved.

The comparison is cumulative steps 01–10 versus the step-00 baseline, not an isolated step-10 A/B. Device, display resolution/density and CompilationMode.None match; thermal status was 1 before and 2 after this run versus 2 for the baseline, and account/app state has evolved. The directly attributable source change removes exactly the intentional 1,800 ms wait.

An additional real-app benchmark probe shows one reminder re-arm across Home → Practice → Android Settings → Back, one status/support query on Practice entry, and one additional query on return from Settings. Initial entry no longer produces the cancel/restart pair; actual resume still revalidates. Evidence: `build/step10/voice-probes.json`, `step10-voice-settings-probe.log`. A launcher-intent probe returned to Home rather than resuming Practice, so it was not used as evidence of a settings-resume query. Final debug APK was restored to the phone.
