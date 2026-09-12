# Step 12 — Rendering work and measured cleanup

Implemented and locally verified 12 September 2026. Covers RW-11/12, POLL-05, the smaller cleanup candidates, and the rendering/motion decisions in RW-14 and UI-10. The complete release matrix remains step 13.

## Playback ownership and precision

Practice now collects playback state in its player controls and word-highlight component. The player passes position as a deferred read to the progress indicator; a derived whole-second value updates the time label. Position changes no longer recompose the player card. Play/pause descriptions, progress semantics, transliteration, the existing layout and theme colors remain.

`BenchmarkAudioPlayer` still samples the real MediaPlayer position every 50 ms for word selection and selected-word/neighbor-window stopping. Cosmetic position publication uses every fourth sample (nominally 200 ms). Start and pause publish immediately; completion and release reset the displayed state. Word indices retain StateFlow equality filtering. This does **not** claim fewer MediaPlayer precision checks or change speech recognition, silence watchdogs, scoring or finalization.

When Practice stops or leaves composition, it pauses reference playback and stops self-audio playback without haptic feedback. This handles saved navigation entries whose ViewModels remain alive. Pause, completion, stop and release continue cancelling the tracker; replay replaces the previous segment boundary.

### Same-device optimized playback sample

Both samples use the bundled reference, the same Pixel 7 Pro, 1440 × 3120 at 560 dpi and the user's 0.85 font scale, and ten seconds between play and pause. The before APK contains counters but the previous rendering/player behavior. Both APK hashes and exact durations are in [the aggregate results](step-12-results.json); snapshots and `gfxinfo framestats` are under `build/step12/`.

| Work during the sample | Before | After |
| --- | ---: | ---: |
| MediaPlayer precision samples | 198 | 198 |
| Cosmetic position publications, including pause | 198 | 51 |
| Player-card recompositions after initial composition | 197 | 2 |
| Word-display recompositions after initial composition | 11 | 12 |
| Rendered frames | 288 | 157 |
| `gfxinfo` janky frames | 7 (2.43%) | 3 (1.91%) |
| Frame duration p95 | 13 ms | 13 ms |
| GPU duration p95 | 3 ms | 7 ms |

The outer Practice function's counter did not increment during either sample. In the old code, position is read inside Scaffold's content lambda; the counter at the outer function does not measure that nested restart scope. These measurements establish removed player-card work, **not** that the whole screen previously recomposed at 20 Hz. Highlight differences of one update reflect sample boundaries. The isolated samples do not establish a general frame-latency, GPU or battery improvement; the GPU percentile increased. After navigating Home during playback, a further 2.096-second snapshot interval recorded **zero** playback samples/publications or player/word compositions.

## Shared loading animation

The 16 Progress placeholders now share one `ShimmerGroup` clock. Each clipped placeholder reads the phase in `drawBehind`, keeping the gradient's theme colors, original 0–1000 trajectory, one-second easing and geometry. Standalone skeleton cards/rows share one clock across their own placeholders. Hidden lifecycle owners remove the animation; resume starts one clock again. Compose's normal system animation-scale handling is retained.

The same Pixel Compose harness advanced 60 animation frames after initial settling. Before: **960** shimmer modifier compositions. After: **0**. A separate test verifies one clock for the entire loading group, disposal on STOP and one new clock on resume. The before count is a composition-work measurement, not a GPU draw count or a wall-time benchmark.

## Small repeated work

- Targets memoizes its current/completed-history selection using active target, completed targets and current stable identity as the relevant keys.
- Session history creates one immutable `DateTimeFormatter` per list/locale/zone, then remembers each row's text by timestamp and formatter. One list-level resume/timezone listener refreshes the zone; Android locale configuration changes replace the formatter. No mutable formatter is shared across threads.
- Teaching pages retain Firestore's existing pinned-descending/publication-descending query order while filtering invalid or invisible records. The duplicate stable pinned sort is removed. Tests cover server/cache ordering, pinned ties, publication ties, rejected records and the original raw-page cursor/has-more contract.

These are bounded cleanups; no perceptible speedup is claimed for them.

## Rendering candidates and motion

The optimized Macrobenchmark harness records 20 before and 20 after navigation iterations, including the complete 320 ms transitions. An opt-in `renderingCandidates` journey records 20 iterations of Daily Chart → Accuracy → Overview twice, allowing 1.1 seconds after each selection to include the complete 800 ms reveal. These use actual app data and the existing Perfetto configuration (frame timeline, scheduling and graphics); they are not synthetic chart microbenchmarks.

| Optimized capture | Frames | CPU duration p95 | Frame overrun p95 | Frames with positive overrun |
| --- | ---: | ---: | ---: | ---: |
| Before: navigation | 2,652 | 9.17 ms | 3.50 ms | 5.84% |
| After: navigation | 2,646 | 9.14 ms | 3.22 ms | 6.08% |
| After: chart entries/re-entries | 9,381 | 9.11 ms | −2.45 ms | 2.22% |

Negative overrun means a frame completed ahead of its deadline. The after run's recorded thermal status was 0 at both ends. Navigation tap-to-usable medians before → after were Home 261.5 → 300.0 ms, Progress 289.0 → 293.3 ms and Settings 284.6 → 294.4 ms; p95 values are retained in the JSON. This cleanup is **not a measured navigation speedup**. Navigation still misses the audit's provisional <5% jank and cached-content p95 targets; those remain explicit step-13 acceptance limits.

Navigation duration, layered Scaffolds/Surfaces, artwork fades and chart entrance animations are retained. The chart journey stays below the provisional 5% positive-overrun threshold, so there is no measured reason here to remove its intended reveal or cache seven-point path calculations. Navigation's CPU p95 is essentially unchanged, and shortening a slide would not establish that content is ready sooner.

The optimized Progress overdraw screenshot (`build/step12/overdraw-progress.png`) confirms substantial overlapping drawing: the background, artwork and cards show red overlay regions. It does **not** isolate the marginal GPU duration of each Surface. The existing layers are retained because nested Scaffolds own different bars/insets and the ambient gradient has a visible purpose; this run supplies no measured component-removal benefit to justify a visual/layout change. This is a documented retention decision, not a claim that overdraw is free or absent. An isolated layer-removal A/B experiment can be reopened if step 13 identifies GPU-bound navigation as the bottleneck.

Four debug design previews were inspected at light/dark themes and 1.0/1.6 font scales. Player labels, controls and card colors remain legible. The existing Devanagari word wrapping remains visible and was not redesigned by this performance change. A real populated Daily Chart renders its final bars with system animator scale zero (`chart-real-reduced-motion.png`); the debug chart fixture is empty and is not used as proof of animated-data behavior. Font scale, animation scale, overdraw mode and screen timeout were restored, and the debug APK was reinstalled for normal use.

## Verification and limits

- **631 unit tests pass:** 367 domain, 147 data, 117 app. New MediaPlayer regressions verify word changes between cosmetic publications, exact segment stopping between those publications, replacement, pause/resume, completion and release cancellation. Existing speech safeguard tests pass unchanged.
- **Eight Pixel device tests pass:** three rendering tests, two real bundled-MediaPlayer tests and three Progress demand/lifecycle tests. Real first/middle/final word tests check stopping within the selected endpoint plus a 150 ms device-scheduling allowance, with completion reset accepted at the media endpoint.
- **Three optimized Macrobenchmark tests pass:** one before navigation capture and two after captures (navigation and chart candidates), each with 20 measured iterations. Raw Perfetto traces and benchmark JSON are retained in `build/step12/traces-before/` and `traces-after/`.
- Debug and optimized benchmark APKs build; lint reports **zero errors, 14 warnings and three hints**. The initial compile exposed one debug design-preview caller of the changed audio-bar API; that caller was updated and the full build passed.
- Final build: `build/step12-final-build.log`. Device results: `build/step12/device-tests.txt`. The summary is reproducible with `python scripts/summarize-rendering.py` after retaining the raw files in `build/step12/`.

No database schema, counting, cloud rules or deployment change is involved. Device timings are specific to this Pixel and fixture. Full history sizes, two-device recovery, broad release scenarios and performance budgets remain step 13.
