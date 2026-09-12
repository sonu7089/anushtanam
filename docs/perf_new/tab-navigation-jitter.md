# Tab navigation jitter: findings and resolution plan

Date: 12 September 2026  
Status: Investigation and proposal only; no application code changes  
Repository revision at document preparation: `71d9da1`

## Problem and conclusion

Switching between bottom navigation tabs produces a slight hitch or uneven motion. Home to Practice is one reported example; the concern applies to other tab combinations too.

The strongest confirmed bottleneck is expensive Compose measurement and composition on the main thread during navigation. An inspected optimized-build trace contains UI frames lasting approximately 34–43 ms, with approximately 21–29 ms inside Compose measurement. The app simultaneously uses a 320 ms full-screen horizontal transition, making missed frames visible as uneven movement.

This identifies the bottleneck category, not a single responsible composable. More detailed tracing is needed before attributing all the cost to a particular Scaffold, constraint layout, screen section, or state update. The existing navigation benchmark does not cover Practice, so Practice-specific causes remain candidates rather than measured conclusions.

The recommended approach is to identify and reduce destination layout work, keep unnecessary updates out of navigation, and then tune the motion. Shortening the animation alone does not remove expensive frames.

## Evidence

### Existing aggregate measurements

The [step-12 report](../../doc/perf/step-12-rendering.md) and [results JSON](../../doc/perf/step-12-results.json) record optimized navigation measurements on a Pixel 7 Pro. The benchmark build inherits release optimization and is not debuggable. These captures used `CompilationMode.None`; they do not demonstrate performance with an app-specific Baseline Profile applied.

| Metric | Recorded after-cleanup result |
| --- | ---: |
| Navigation iterations | 20 |
| Measured navigation frames | 2,646 |
| Frames with positive deadline overrun | 6.08% |
| CPU frame duration p95 | 9.14 ms |
| CPU frame duration p99 | 35.39 ms |
| Frame overrun p95 | 3.22 ms |
| Frame overrun p99 | 35.59 ms |
| Home observed tap-to-usable median / p95 | 300.0 / 402.6 ms |
| Progress observed tap-to-usable median / p95 | 293.3 / 336.4 ms |
| Settings observed tap-to-usable median / p95 | 294.4 / 425.8 ms |

The [benchmark journey](../../macrobenchmark/src/main/java/com/mantrasiddhi/macrobenchmark/AppBaseline.kt) visits Progress, Settings, and Home. It waits after each selection to include the complete transition in frame capture. Tap-to-usable uses UI automation and readiness tags; it is distinct from animation completion and includes observation overhead.

The earlier cleanup did not establish a navigation speedup: CPU p95 remained essentially unchanged and positive-overrun percentage changed from 5.84% to 6.08%. These results should not be generalized to every device, account, refresh rate, or tab.

### Direct inspection of an existing trace

Trace inspected during this investigation:

`build/step12/traces-after/AppBaseline_navigation_iter010_2026-09-12-15-28-16.perfetto-trace`

| Trace slice | Duration | Observation |
| --- | ---: | --- |
| `Choreographer#doFrame`, slice 41444 | 42.71 ms | Long main-thread UI frame |
| `AndroidOwner:onMeasure`, slice 41774 | 28.60 ms | Measurement within that frame |
| `Choreographer#doFrame`, slice 72362 | 33.56 ms | Another long main-thread UI frame |
| `AndroidOwner:onMeasure`, slice 72644 | 20.79 ms | Measurement within that frame |

Thread-state overlap for slice 41774 shows approximately **27.56 ms Running** and **1.04 ms Runnable**. Most of that measurement interval was actual CPU execution, not blocking on a network response or waiting for CPU scheduling.

Descendants of that measurement include five `Compose:recompose` slices totaling approximately 10.34 ms, five `Compose:applyChanges` slices totaling approximately 3.25 ms, and 23 text-layout initialization slices totaling approximately 2.62 ms. These are nested timings and must not be added indiscriminately to the parent measurement or to one another.

FrameTimeline also contains app-deadline misses and dropped-frame entries. Its raw entry counts are not interchangeable with the Macrobenchmark frame sample count or overrun percentage above.

The trace establishes a main-thread composition/layout problem in the inspected navigation journey. It does not prove that GPU work is irrelevant, identify every slow frame's cause, or establish the exact contribution of an individual app component.

### Current implementation and device observations

| Area | Observed behavior | Interpretation |
| --- | --- | --- |
| [Navigation transitions](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/MantraNavGraph.kt) | Enter and exit both use full-screen horizontal slides with a 320 ms tween for tab changes | Destination work occurs while outgoing and incoming content participate in the transition; duration contributes to perceived sluggishness but is not itself proof of jank |
| [Bottom navigation](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/FloatingNavigationBar.kt) | Two nested `BoxWithConstraints`; animated selection offset and colors | Constraint subcomposition is worth inspecting, but these containers are not yet individually implicated by measurements |
| Same bottom navigation | Uses `saveState`, `restoreState`, and `launchSingleTop` | Navigation state retention already exists; preserving back-stack state does not mean every destination's UI remains composed |
| [Home](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt) | Uses a keyed `LazyColumn` | The older eager-Home audit finding should not be treated as current |
| [Practice](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/practice/PracticeScreen.kt) and [Settings](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SettingsScreen.kt) | Use scrolling Columns within screen Scaffolds | Offscreen content can participate in entry composition/measurement; assess actual cost before restructuring |
| [Artwork](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/AppArtwork.kt) | Sized asynchronous image loading; `crossfade(false)` | Do not prescribe fixing full-resolution synchronous artwork decoding or removing artwork crossfades as if they were still present |
| Connected Pixel during investigation | Installed package reported `DEBUGGABLE` | Debug overhead may amplify the user's experience; optimized recordings show that it cannot explain the entire issue |

## Practice-specific candidate: speech availability checks

[VoiceModelGate](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/VoiceModelGate.kt) gates Practice and Recitation. [VoiceModelViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/voicemodel/VoiceModelViewModel.kt) checks availability at initialization and refreshes on subsequent resumes. Its process-wide readiness hint already prevents a previously ready gate from flashing the checking spinner on every visit.

[VoiceModelManager.checkStatus](../../core/data/src/main/java/com/mantrasiddhi/data/speech/VoiceModelManager.kt) executes on Main, discovers available backends, creates recognizers and requests recognition support. The support response is asynchronous: a suspended wait or configured timeout must not be described as the main thread being blocked for that duration. Setup and service interactions can still add work around screen entry or transition completion.

This needs a Home ↔ Practice trace before assigning it a share of the reported jitter. Speech API operations that require Main must remain on Main.

## Proposed resolution order

### 1. Attribute the slow work before restructuring

- Capture Home ↔ Practice and the existing Home/Progress/Settings journey in an optimized, profileable build.
- Separate first entry after process launch from repeat visits, including returns after screen-data subscription timeouts.
- Add detailed Compose tracing or narrowly scoped diagnostic sections around destination entry and expensive sections. Keep attribution captures separate from final performance comparisons if instrumentation adds overhead.
- Correlate slow frames with composition, measurement, text layout, state emissions, speech-service setup, and render-thread/GPU work.
- Record the active refresh rate and thermal conditions. Frame budgets are approximately 16.7 ms at 60 Hz and 8.3 ms at 120 Hz; a measurement taking 28.6 ms alone exceeds either budget.

Deliverable: the specific expensive components or operations, when they run, and their contribution to slow frames.

### 2. Reduce destination composition and measurement cost

- Inspect the root and destination Scaffolds and the navigation bar's nested constraint layouts. Simplify only demonstrably redundant work while preserving inset ownership, bars, responsive labels, and layout behavior.
- For long scrolling destinations, consider stable-key lazy sections so opening a tab does not require measuring all offscreen content. Home already uses this structure.
- Keep simple, small groups as ordinary Rows or Columns. Lazy layouts and `BoxWithConstraints` introduce subcomposition overhead; replacing every layout with a lazy layout is not a blanket optimization.
- Inspect repeated text measurement and expensive calculations performed during composition. Cache only reusable work whose input dependencies are correct.

Deliverable: smaller entry-time CPU/layout spikes with unchanged visible content and restored scroll behavior.

### 3. Reduce unrelated updates during transitions

- Keep high-frequency state reads close to the components that display them, so a control update does not invalidate a large screen subtree.
- Reuse already available destination data on return while refreshing as needed. Verify existing ViewModel retention and lifecycle-aware collection before introducing another cache.
- Defer eligible animation state reads to placement or drawing. The selection pill already uses the lambda form of `offset`; that particular optimization is already present.
- Investigate repeated state emissions or refresh launches on navigation; change only those shown to cause unnecessary work.

Deliverable: fewer avoidable composition/layout passes while a transition is active, without stale account data or background observers that outlive their purpose.

### 4. Address Practice checks if profiling confirms a contribution

- Reuse valid speech availability across ordinary tab switches instead of repeating service setup unnecessarily.
- Define invalidation for downloads, return from speech settings, recognition errors, and any chosen freshness policy. Returning from an external settings screen must still update availability.
- Coalesce concurrent checks and preserve the existing ready content during a nonblocking revalidation.
- Keep Main-required speech operations on Main and retain missing-model, retry, and explicit unverified-trial behavior.

Deliverable: reduced redundant check/setup work with unchanged speech-readiness correctness. This is a proposed design to validate, not a measured fix.

### 5. Tune motion independently

After reducing layout cost, compare the existing slide with a shorter, subtler transition and an immediate content switch that retains selection-pill animation.

Choose based on both visual preference and measurements. A shorter transition may feel faster, but a 30–40 ms UI frame can still hitch. Do not delay rendering the destination until the slide finishes merely to move expensive work outside the measured animation window.

Deliverable: a reviewed motion choice with time-to-usable and full-transition frame results, rather than only a reduced animation duration.

### 6. Verify production compilation behavior

Audit Baseline Profile generation, packaging, and installation. Ensure representative tab journeys, including Practice, are covered and compare performance with the profile applied. Preserve a comparable compilation mode when evaluating individual code changes.

Baseline Profiles can reduce interpretation/JIT overhead; they do not eliminate excessive layout work. The existing `CompilationMode.None` results should remain a separately labeled baseline.

## Verification and acceptance

Run at least 20 measured iterations per selected journey/configuration, separating warmup. Compare the same device, account data, screen density, font scale, system animation settings, refresh-rate conditions, and compilation mode. Record APK identity and thermal state.

| Scenario | What to check |
| --- | --- |
| First Home → Practice and Home → Recitation | Gate state, speech setup, entry layout, time to meaningful content |
| Repeated Home ↔ Practice | Cached readiness/data, composition/layout cost, no repeated spinner |
| Home → Progress → Settings → Home | Comparable regression journey against the existing measurements |
| Rapid alternating tab selections | Interrupted transitions, correct selected tab, correct destination and back stack |
| Return after an idle interval | Restarted subscriptions or refresh work, retained usable content |
| Navigate away during reference playback | Playback stops as intended and does not leave hidden update work |
| Light/dark themes and large fonts | Insets, label visibility, wrapping, artwork, scroll position and touch targets |
| Speech settings/download return and missing model | Correct readiness invalidation, retry and trial flows |

Report CPU frame duration and frame-overrun p50/p95/p99, positive-overrun percentage, tap-to-usable timing, and the attributed layout/composition slices. Measure content readiness separately from transition completion and include the full transition in capture.

Proposed acceptance criteria, to be agreed before implementation:

- No repeatable visible hitch in the tested tab journeys, including Practice.
- Remove or materially reduce the identified 21–29 ms measurement spikes; explain any remaining slow-frame clusters.
- Reach the existing audit's provisional **less than 5% positive-overrun** threshold for comparable navigation captures, with improved tail timings. This threshold alone is not proof that a visible hitch is gone.
- No regression in time to usable content, navigation state, speech readiness, playback lifecycle, accessibility, or layout.
- Retain before/after trace and APK identities so any claimed improvement is reproducible. Set numeric content-readiness targets after measuring the currently uncovered Practice journey.

## Scope and cautions

This document authorizes no implementation or motion redesign by itself. No app code, device settings, dependencies, or installed APK were changed for its preparation. Earlier trace inspection used an existing profiler on the connected phone; its temporary trace copy was removed.

Do not infer that every ViewModel is recreated on every tab switch, that every nested Scaffold should be removed, that all work under `onMeasure` is pure geometry calculation, or that a shorter slide proves faster content readiness. GPU overdraw remains a secondary investigation candidate; current evidence does not establish it as the dominant cause.

## References

- [Existing rendering audit and measurement limits](../../doc/perf/step-12-rendering.md)
- [Existing aggregate results](../../doc/perf/step-12-results.json)
- [Navigation benchmark implementation](../../macrobenchmark/src/main/java/com/mantrasiddhi/macrobenchmark/AppBaseline.kt)
- [Android: Practical Compose performance investigation](https://developer.android.com/codelabs/jetpack-compose-performance)
- [Android: Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Android: Compose phases and performance](https://developer.android.com/develop/ui/compose/performance/phases)
- [Android: Compose performance and Baseline Profiles](https://developer.android.com/develop/ui/compose/performance)
