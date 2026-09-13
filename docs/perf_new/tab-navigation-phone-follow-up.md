# Tab jitter: physical-device follow-up

Date: 13 September 2026.

The user still saw jitter after the first implementation. The emulator fixture had bypassed the real Home/Progress state producers and the speech gate, so it could not validate those parts of the journey. The earlier navigation-bar improvements remain useful, but did not resolve the reported experience.

## Confirmed causes

The connected Pixel 7 Pro was running the supplied debug APK. A real signed-in Home → Practice → Home → Progress → Settings → Home trace recorded main-thread frames up to **102.2 ms**, including **70.24 ms** in Compose measurement. No recording or audio playback was started.

An optimized diagnostic build reproduced the screen-entry work with lower timings. Detailed composition tracing showed **84 HomeScreen compositions for ten returns to Home**, and **five Progress loading-skeleton compositions for five visits**. Settings entry measurement reached **39.97 ms** in this attribution capture. The warm timing capture, without detailed tracing, still had main-thread frames up to **29.54 ms** and 19 frames longer than 16.67 ms across 25 switches.

Two source-level behaviors explain the avoidable churn:

- Home and Progress used `WhileSubscribed(0, replayExpirationMillis = 0)`. Leaving a tab immediately stopped its producers **and discarded its published UI state**. Returning first displayed a loading state. Home then restarted each section through a separate loading emission. Progress had a section cache but reset its public StateFlow to `Loading` before restoring that cache.
- Every peer-tab switch slid both full screens horizontally for 320 ms. Incoming screen construction, intermediate loading layouts, and outgoing-screen disposal occurred during that motion. The warm original capture contains 1,019 `AndroidOwner:onMeasure` calls over 25 switches. These calls have varying costs; their count alone is not a jank measurement.

This is UI composition/layout work, not a heavy evaluation algorithm running when a tab is tapped. Practice's idle content was composed once per visit in the attribution capture. Speech availability is still checked on entry/resume; this follow-up does not claim that those checks are free or eliminate them without evidence.

## Implementation

- [ProfileUiState](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/state/ProfileUiState.kt) retains the last published state while no UI observes it and cancels the screen's data subscriptions. A lightweight session observer remains attached so profile/account replacement and sign-out clear retained state even while the tab is hidden.
- [HomeViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt) retains each resolved section for the same profile/account. Re-subscribing refreshes the data without first replacing its values with placeholders. Japa still uses the calendar's current day.
- [ProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressViewModel.kt) retains its published content as well as its existing section cache. A refresh preserves content instead of rebuilding the skeleton. Hidden history/statistics subscriptions still stop.
- [Navigation transitions](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/MantraNavGraph.kt) switch directly between peer tabs. The selection pill still animates. Detail pushes/pops retain their 220 ms slide. Screen layouts and navigation save/restore behavior are unchanged.
- The benchmark-only [PerfSnapshotReceiver](../../frontend/android/src/benchmark/java/com/mantrasiddhi/perf/PerfSnapshotReceiver.kt) accepts a shell-protected `traceCompositions` flag for the actual app activity. Attribution can now include the signed-in screen producers instead of substituting fixture data. It is disabled for timing captures and absent from debug/release APKs.

## Measurement method

All captures here use the physical Pixel 7 Pro, the same signed-in data, screen size and animation settings. The optimized builds use the existing benchmark variant (R8 enabled, debug signing, not debuggable). Installing with `adb install -r` retained app data. No app-data clear, sign-out, recording, or fixture account was used.

Each optimized run visits Practice, Home, Progress, Settings, Home five times (25 switches), with approximately 1.2 seconds between taps and a 40-second trace. Detailed attribution runs also warm up the screens; timing runs disable the Compose tracer. The comparison uses `before-timing-clean` and `after-timing`. An earlier `before-timing` run overlapped trace-processor analysis on the phone and is excluded.

The timing summaries use outer `Choreographer#doFrame <number>` slices on the app's main thread, excluding nested resynchronization slices. Their durations include CPU scheduling and are **not** Macrobenchmark's `frameDurationCpuMs`. FrameTimeline records are retained in the raw capture, but are not substituted for a normalized jank percentage. Removing a full-screen animation changes the number of produced frames, so counts of long frames and total work are reported alongside percentiles.

Raw APKs, traces, capture configuration, tap sequence, work counters, thermal/display state, and SQL are preserved under `build/tab-jitter/phone-investigation/`. The diagnostic capture script there is specific to the observed six-tab layout and phone serial; coordinates must be rediscovered from UI Automator before reuse on another layout/device.

## Verification

The optimized APK, debug APK and instrumentation APK build successfully. **121 app unit tests pass**, including retained content during slow refreshes, immediate cancellation of hidden data subscriptions, account replacement with the same local profile ID, hidden sign-out, midnight refresh and existing progress pagination behavior.

**28 UI tests pass on the Pixel**: four navigation tests, 12 Lotus UI tests, four backup/status tests and eight progressive-entry tests. The new motion regression checks that the destination tab is at its final horizontal position within 64 ms of test-clock time while a detail push is still sliding at that point. Existing coverage includes large fonts, narrow widths, RTL, rapid selection, Settings controls and scroll restoration. This checks transition behavior, not a promise of 64 ms wall-clock latency.

After testing, the optimized benchmark APK was restored on the phone. Updated `app-debug.apk` and `app-performance.apk` copies are in the repository root; the latter is the optimized diagnostic build used for the phone evaluation, not a store-distribution release. Root APK copies match their respective build outputs. All new logs stay under `build/tab-jitter/phone-investigation/`. The phone locked after the tests, so a final appearance screenshot was not used as evidence; perceived smoothness remains subject to the user's check.

## Phone results

Both timing captures reported 120 Hz, animator scale 1.0 and thermal status 0 before/after the run. These are one paired 25-switch journey with natural runtime warmup; no forced AOT compilation was applied. [Machine-readable results](tab-navigation-phone-results.json) include the APK hashes.

| Metric | Original optimized APK | Updated optimized APK |
| --- | ---: | ---: |
| Main-thread frame p50 | 1.30 ms | 0.78 ms |
| Main-thread frame p95 | 7.99 ms | 5.25 ms |
| Main-thread frame maximum | 29.54 ms | 32.83 ms |
| Frames longer than 8.33 ms | 56 | 46 |
| Frames longer than 16.67 ms | 19 | 25 |
| Frames longer than 33.33 ms | 0 | 0 |
| Total main-thread frame time | 2,728.44 ms | 2,185.73 ms |
| `AndroidOwner:onMeasure` calls | 1,019 | 75 |
| Total `AndroidOwner:onMeasure` time | 541.60 ms | 414.28 ms |
| Warm Progress shimmer starts, from work-counter deltas | 5 | 0 |
| HomeScreen compositions, separate attribution run / ten returns | 84 | 40 |

The change removes the repeated Progress loading flash and greatly reduces full `onMeasure` passes during navigation. Placement/layout work still occurs while the selection pill animates; the lower `onMeasure` count must not be described as eliminating all layout work. The typical frame and total-work results improved, but **worst-case entry frames did not improve in this pair**. Screen reconstruction still costs time on first/repeated entry, and no zero-jank claim is justified. The main perceptual change is that content appears directly instead of these entry costs interrupting a 320 ms full-screen slide.

## Cleanup after accepting direct tab switches

The legacy route-order table and duplicate transition checks were removed after the user accepted direct tab switching. Both transition directions now share one policy: peer tabs, identical destinations and the initial auth-gate handoff have no screen animation; other pushes slide left and pops slide right over 220 ms. This also gives destinations absent from the old table (such as messages) the normal push direction. The selection-pill animation is unchanged. The measurements and APK hashes above precede this source cleanup.
