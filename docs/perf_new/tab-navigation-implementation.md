# Tab navigation: implementation and verification

This is the first-pass emulator report from 12 September. The user's subsequent phone report and the second implementation are documented in [physical-device follow-up](tab-navigation-phone-follow-up.md).

This follows the [original investigation](tab-navigation-jitter.md). The changes keep the existing screen designs, 320 ms horizontal tab transitions, selection-pill motion, and navigation save/restore behavior.

## Changes

### Isolate navigation-bar animation work

[FloatingNavigationBar.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/FloatingNavigationBar.kt) previously read every tab's animated color in one shared composition scope. A five-switch attribution trace recorded 41 executions of that shared content scope, versus five executions of the outer bar. Each animation tick could revisit the row and its siblings.

Each tab now owns its color-state read in a separate composable. The shared bar still changes when the selected route changes; color ticks update their own tabs. The pill already used a deferred placement read and retains it.

The second nested `BoxWithConstraints` is removed. The outer constraint scope already supplies the width, so the inner slot width can be calculated directly after allowing for the existing 4 dp padding on each side. The outer responsive breakpoint, large-font behavior, relative placement, padding, shadow and semantics remain.

### Keep route changes out of the graph owner

[MantraNavGraph.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/MantraNavGraph.kt) now observes the current route inside small scopes for route-dependent effects and bottom-bar visibility. Selecting a tab no longer invalidates `ResolvedMantraApp` through its own back-stack state read.

Auth guards, notification/reminder routing, and detail-screen bar visibility retain their conditions. This removes a source of parent recomposition; it does not claim that every former parent recomposition necessarily rebuilt every destination.

### Defer offscreen Settings content

[SettingsScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SettingsScreen.kt) uses keyed lazy sections for the intro, account, preferences, Sandhya entry, and backup/sign-out group. Opening the page no longer requires composing all sections below a small viewport. Section order and spacing remain, and the lazy list saves its scroll position.

Existing preference and backup UI tests now scroll through the lazy container to find content that may not yet be composed.

## Test and benchmark tooling

- [TabNavigationUiTest.kt](../../frontend/android/src/androidTest/java/com/mantrasiddhi/presentation/TabNavigationUiTest.kt) checks route-observer isolation, rapid tab selection with right-to-left layout, width/font changes, offscreen Settings deferral, and scroll restoration.
- [NavigationFixtureActivity.kt](../../frontend/android/src/benchmark/java/com/mantrasiddhi/perf/NavigationFixtureActivity.kt) is present only in benchmark APKs and restricted to callers with the `DUMP` permission. It renders fixed-data Home, Progress and Settings, plus the real idle Practice screen and ViewModel. It creates no account and performs no recording.
- [TabNavigationBenchmark.kt](../../macrobenchmark/src/main/java/com/mantrasiddhi/macrobenchmark/TabNavigationBenchmark.kt) captures Practice → Home → Progress → Settings → Home, including the complete transitions. Fixture runs are opt-in.
- Optional composition tracing uses the Compose runtime tracing interface in the benchmark source set only. It is enabled for attribution runs and disabled for the before/after timing comparison.
- [benchmark-tab-navigation.ps1](../../scripts/benchmark-tab-navigation.ps1) preserves APK hashes, environment, instrumentation output, readiness observations and raw Perfetto traces under `build/tab-jitter/`.
- [summarize-tab-navigation.py](../../scripts/summarize-tab-navigation.py) checks that comparison environments match and rejects attribution runs as timing comparisons.

## Measurement scope

The user requested emulator verification after ADB reported no connected phone. A separate emulator configuration and fresh data live under `build/tab-jitter/avd/`; the existing Lotus emulator's data were preserved. An initial attempt with the existing data hit a pre-existing Room migration/index mismatch before navigation, so those results were excluded.

The fresh emulator runs Android 17, 1080 × 2400 at 420 dpi, font scale 1.0. Hardware-accelerated measurements use the NVIDIA RTX 4060 through the emulator OpenGL translator, with Vulkan disabled. Both APKs use `CompilationMode.None` and identical motion settings. No app dependencies or compiler configuration were changed.

The initial software-rendered run is unsuitable for phone performance claims. The first hardware-accelerated baseline also showed substantial variation: CPU frame medians fell sharply near its final iterations. Its apparent large before/after improvement must not be credited to the code. A later, reverse-order repeat of the original APK checked this effect. That repeat also varied, starting with low frame medians and becoming slower midway through. The run ID `before-stable` names that repeat; it does not establish a stable environment. Emulator/host variation therefore remains a confounder in the timing comparison.

The fixed-data fixture isolates component and navigation rendering. It does not exercise production auth/profile resolution, Home's loading-state pipeline, remote backup/account controls, or speech readiness. The root application's route-observer change is covered by UI isolation checks rather than an end-to-end signed-in benchmark. Emulator timings cannot establish the Pixel's final jank rate or satisfy a physical-device acceptance budget.

## Results

### Removed composition work

For the five-switch attribution journey, the shared navigation content scope executes **41 times in the original APK and five times in the updated APK**. The updated trace instead records 103 executions of the individual `NavigationTab` composable, covering initial selection updates and per-tab color ticks. This confirms that color changes no longer restart the shared bar content. It does not mean that all tab animation work has disappeared.

The attribution captures use the same five-switch fixture, but the original capture used software graphics and the updated capture used host graphics. Compare their scope behavior, not their elapsed CPU totals or a percentage of frames. Frame cadence can affect animation tick counts. The UI regression independently verifies that changing routes does not recompose the graph's parent scope.

Raw attribution traces are under `build/tab-jitter/attribution-before/` and `build/tab-jitter/attribution-after/`. The original shared scope is reported at `FloatingNavigationBar.kt:77`; the updated shared scope is at `FloatingNavigationBar.kt:80`. Source locations refer to the respective captured APKs. The post-change query output is saved in `build/tab-jitter/composition-after.txt`.

### Observed emulator timings

[Machine-readable results](tab-navigation-results.json) compare the later original-APK run (`before-stable`) with the updated-APK run (`after-host`). Both have 20 iterations, identical graphics/settings, and composition attribution disabled. Each run measures 100 tab switches. These are observations with the variability described above, not a controlled estimate of the code's isolated speedup.

| Metric | Original APK, repeat | Updated APK |
| --- | ---: | ---: |
| CPU frame duration p50 | 15.27 ms | 5.09 ms |
| CPU frame duration p95 | 44.67 ms | 20.57 ms |
| CPU frame duration p99 | 64.44 ms | 23.67 ms |
| Frame overrun p95 | 46.73 ms | 7.68 ms |
| Frames with positive overrun | 59.63% | 17.96% |
| Practice observed tap-to-usable median | 208.1 ms | 179.8 ms |
| Practice observed tap-to-usable p95 | 472.2 ms | 234.1 ms |
| Settings observed tap-to-usable median | 299.9 ms | 186.0 ms |

The updated sample is better overall, but the variability prevents promising these gains on a phone. The provisional less-than-5% positive-overrun target is **not met by this emulator sample**. A signed-in physical-device run remains necessary to evaluate the user's final experience and the remaining entry-time work.

### Builds, behavior and visual checks

- Optimized benchmark APK, debug APK, and debug instrumentation APK build successfully.
- **117 app unit tests pass**, with zero failures or errors.
- **27 emulator UI tests pass**: three new navigation/layout regressions, 12 Lotus UI tests, four backup/status tests, and eight progressive-entry tests.
- Coverage includes rapid selection during animation, right-to-left navigation, narrow widths, large fonts, parent/individual tab availability, preference selection, backup/sign-out reachability, lazy-section deferral, saved scroll position and entry boundaries.
- Before/after optimized Settings and Practice screenshots were visually inspected. Typography, artwork, content spacing, controls and navigation appearance are preserved in the inspected light-theme layout. Screenshots are in `build/tab-jitter/{settings,practice}-{before,after}.png`.
- Build and test logs are under `build/tab-jitter/`, including `implementation-build-2.log`, `ui-test-build.log` and `ui-tests.log`. No new logs were placed in the repository root.

These checks establish the implemented work reduction and tested behavior; they do not establish zero jitter on every tab/device. No phone installation, account creation, cloud change, speech behavior change, or database migration fix was performed.

## Reproduction

With a selected emulator/device and a working Android JDK/SDK, capture the original APK before applying production changes, then run the same journey with the updated APK. Preserve the APKs when comparing revisions:

```powershell
./scripts/benchmark-tab-navigation.ps1 -Serial emulator-5554 -RunId before -Iterations 20 -SkipBuild -Apk path/to/original-fixture.apk
./scripts/benchmark-tab-navigation.ps1 -Serial emulator-5554 -RunId after -Iterations 20
python -B scripts/summarize-tab-navigation.py build/tab-jitter/before build/tab-jitter/after --output docs/perf_new/tab-navigation-results.json
```

Pass `-Adb` when `adb` is not on PATH. Use a separate run ID with `-TraceCompositions -Iterations 2` for attribution. Keep emulator boot/graphics warm-up and other builds out of measured runs. The JSON `animatorScale` value `null` is the output of Android's settings command for an unset override, not a request to disable animation.

## Scope retained for separate investigation

Home's account-scoped state lifetime, voice availability revalidation, Baseline Profile packaging, and transition design are unchanged. Their behavior should be measured in a signed-in journey before making further changes. The earlier Pixel trace establishes the original measurement/layout bottleneck, but this implementation must not be presented as a new measured Pixel result.
