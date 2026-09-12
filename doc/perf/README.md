# UI performance audit

**Start implementation with the [master fix roadmap](fix-roadmap.md).** It consolidates all audits into one ordered checklist and supersedes their individual implementation sequences.

Reviewed 12 September 2026 against commit `5c5aaba`.

This review covers launcher-to-usable-screen latency, navigation, scrolling, and image loading in the Android Compose application. It contains findings and an implementation proposal; application code and production images have not been changed.

The strongest starting points are the **1.8-second artificial splash wait**, synchronous decoding of oversized artwork, repeated full-history analytics, and server-first loading of Messages. Smaller images alone will not remove the other delays.

See [image delivery design](image-delivery.md) for resolution selection, compression, metadata removal, and quality checks. [Asset inventory](image-inventory.csv) records the measured source files; [the inventory script](inspect_images.py) reproduces it using Python's standard library.

See [lazy and progressive loading](lazy-loading.md) for a screen-by-screen plan to fetch data on demand, show cached content immediately, and load slow sections independently. It also covers request cancellation, pagination and loading/error states.

The follow-up [database correctness/query audit](database-audit.md) reviews counting, migrations, sync and cloud rules with synthetic SQLite and local Firestore reproductions. The [polling audit](polling-audit.md) inventories timers and identifies avoidable CPU work. Their verification results are separate from this original UI-only review.

The [redundant-work audit](redundant-work-audit.md) traces duplicate account loading, repeated subscriptions/calculations, refresh side effects and rendering work, including existing guards and cases that still need device profiling.

## Evidence and limits

The original UI audit traced source code and inspected all 12 PNG files under `frontend/android/src/main/res/drawable-nodpi`. `adb devices -l` returned no connected devices. Consequently, there are no measured startup times, frame timings, decoder durations, cache-hit rates, or before/after performance claims here. No build or application tests were run for that initial audit; subsequent database checks and attempted tests are recorded in the database audit.

“Confirmed” below means the code path or asset property exists. Its contribution to observed device slowness still needs profiling. Configured timeouts are possible waits, not timings measured in this app. Memory figures use `width × height × 4`, an RGBA-equivalent estimate, rather than measured process memory.

## Priority order

| ID | Priority | Finding | Affected experience | Evidence level |
| --- | --- | --- | --- | --- |
| UI-01 | P1 | Splash finishes an 800 ms animation and then waits another 1,000 ms | Launcher startup | Confirmed intentional delay |
| UI-02 | P1 | Large raster resources decode synchronously during composition | First page entry, scroll, transitions | Confirmed loader and dimensions; jank unmeasured |
| UI-03 | P1 | Home eagerly composes cards and their artwork below the viewport | First usable Home screen | Confirmed composition structure |
| UI-04 | P1 | Progress and Home repeatedly process complete history | Navigation with established accounts | Confirmed unbounded queries and downstream CPU work |
| UI-05 | P1 | Messages waits for server before returning cached feed/detail | Opening Messages on slow/offline connections | Confirmed server-first order; 8 s timeout |
| UI-06 | P1 | Startup account resolution and ancillary work gate Home | Returning signed-in startup | Confirmed dependency chain; duration conditional |
| UI-07 | P2 | Notification maintenance performs synchronous storage work on UI entry paths | Launch, resume, notification toggle | Confirmed calls; cost unmeasured |
| UI-08 | P2 | Home computes child progress that its current layout never displays | Parent Home loading and background work | Confirmed unused data dependency |
| UI-09 | P2 | Eager ViewModel collection can outlive visible pages | Navigation after visiting several tabs | Confirmed lifecycle pattern; retained workload needs tracing |
| UI-10 | P3 | Full-page tab slides last 320 ms; performance regression coverage is absent | Perceived navigation speed | Motion duration confirmed; profiling gap |

P1 means implement in the first performance pass; P2 follows or accompanies related changes; P3 requires measured/design validation. These are priorities, not severity claims about crashes or correctness.

## Findings and recommended changes

### UI-01 — Artificial splash delay

[SplashActivity.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/SplashActivity.kt), `SplashScreenContent`, lines 67–79, awaits an 800 ms `animateTo`, then `delay(1000)`, and only then starts `MainActivity`. [AndroidManifest.xml](../../frontend/android/src/main/AndroidManifest.xml), lines 38–50, declares this separate activity as the launcher.

Whenever this splash is created, it intentionally withholds Main for approximately 1.8 seconds, in addition to process creation, composition, image decoding and subsequent account loading. This does not apply to every warm resume or to notification intents that go directly to Main.

**Change:** use the Android splash compatibility API on the main entry activity, with a small launcher asset. End the splash when the minimum local routing state is ready; show recoverable loading within the app for longer work. Do not wait for a decorative animation, artwork, reminder maintenance, or cloud refresh. Android's [splash migration guidance](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate) describes replacing a dedicated splash activity.

**Verify:** launcher cold/warm starts, signed-out Welcome, returning local profile, child setup, recovery and notification entry. Removing this path removes its explicit wait; it does not promise a particular total startup time.

### UI-02 — Full-resolution raster decoding on the UI thread

[LotusDesign.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/LotusDesign.kt), lines 33–45, routes eight artwork types through `painterResource`. Direct raster calls also occur in Home's `PracticeEntry`, `PracticeGuideArtwork`, `GoalArtwork`, `SandhyaHomeCard`, `SandhyaSettingsEntry`, and `PurashcharanaMala`.

Eleven artwork files contain about 1.57 million pixels each (roughly 6 MiB RGBA equivalent). The same resources appear at 48, 56, 72 and 76 dp, as well as in large headers. All are `drawable-nodpi`: shrinking their Compose drawing bounds does not create smaller resource variants or a target-size image request. Android documents that [`painterResource` loads during composition on the main thread](https://developer.android.com/codelabs/jetpack-compose-performance).

**Change:** introduce sized generated derivatives and one shared asynchronous artwork component, detailed in [image delivery design](image-delivery.md). Reuse the existing Coil 2.6.0 dependency. Keep vectors for functional icons and a separate correctly sized launcher resource. Avoid using a large synchronous bitmap as the async placeholder.

Caching can avoid some repeated decoding. This finding does **not** imply every recomposition decodes every image. First use, cache misses, multiple sizes and memory pressure remain relevant.

**Verify:** Perfetto main-thread image decoding and texture upload slices, cold/warm page entries, actual decoded dimensions and cache behavior. Check crispness and alpha on real densities, not just desktop previews.

### UI-03 — Home loads below-the-fold content eagerly

[HomeScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt), `HomeContent`, lines 98–183, uses a scrolling `Column`. It composes the hero, practice entries, goals, Sandhya, guide, Purashcharana, wisdom and Messages entry immediately. Below-the-fold images therefore participate in initial composition.

Home references six distinct large artwork sources through these components: lotus morning, practice book, mala, vessel, emblem and Messages artwork. Their combined source pixel buffers are roughly 36 MiB RGBA equivalent if all six are decoded once. This is a potential footprint illustration, not measured simultaneous memory or an assertion of six independent cached allocations.

**Change:** make each major section a stable-key item in a `LazyColumn`, preserving section padding, accessibility order and restored scroll position. Keep the first viewport cheap and render text/actions independently of artwork readiness. Lazy layout can prefetch near-visible items; it is not a guarantee that absolutely no offscreen work occurs.

**Verify:** trace initial Home composition and scrolling through every card, including large fonts. Confirm the first viewport no longer requires all decorative artwork.

### UI-04 — Repeated history queries and aggregation

[ProgressViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressViewModel.kt), `combineCore` and `combineTrends`, starts five independent session flows: overview, daily counts, accuracy, history, and axis trends. Each currently reaches the unbounded `getSessionsForUser` query in [RecitationDao.kt](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/RecitationDao.kt), lines 24–25. Japa summary adds a sixth session subscription through [JapaHistoryRepositoryImpl.kt](../../core/data/src/main/java/com/mantrasiddhi/data/repository/JapaHistoryRepositoryImpl.kt), lines 13–21, plus all japa entries. These flows start even when only the Overview tab is visible.

[RecitationRepositoryImpl.kt](../../core/data/src/main/java/com/mantrasiddhi/data/repository/RecitationRepositoryImpl.kt), lines 51–55, correctly moves query-result mapping to IO, but also materializes every complete session and decodes word scores. The subsequent maps in `GetRecitationStatsUseCase`, `GetDailyRecitationCountsUseCase`, `GetAccuracyTrendUseCase` and `GetAxisTrendsUseCase` occur downstream of that `flowOn`, in the collecting ViewModel's context. They repeatedly filter, group, sort and convert dates, ordinarily on Main. `JapaHistoryRepositoryImpl` and `GetJapaSummaryUseCase` similarly have no background boundary around their collection-wide transformations.

Home independently reads full history for its dashboard and japa summary. As history grows, this increases startup/page-entry work, allocations, and work on invalidation. Room's query execution is not being alleged to run on Main; downstream Kotlin aggregation is the issue.

**Change:** expose observable SQL projections for counts, scores and bounded chart windows; aggregate by calendar day with the existing timezone semantics. Reuse the existing aggregate query patterns where suitable. Keep exact `BigInteger` japa totals and legacy-session de-duplication. Do not replace them with an overflowing SQL integer sum. Move residual CPU aggregation to an injected Default dispatcher. As an intermediate step, share one per-user source and calculate related projections in one pass.

Load History when its tab opens, with a paged or bounded query and continuation. The History UI already uses a keyed `LazyColumn`; the problem is loading all its data upfront, not missing lazy row rendering.

Also seed Progress's optional cloud summary with an explicit initial local fallback. Currently `combineCore` cannot emit until `summaryFlow()` emits; [FirestoreProgressRepositoryImpl.kt](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirestoreProgressRepositoryImpl.kt), lines 53–69, has no initial value for the snapshot path. Firestore may emit cache quickly, so this is an emission dependency, not proof of a server round trip on every entry. Preserve cloud-restored totals and distinguish “refreshing” from a real empty history.

**Verify:** 0, 1,000 and 10,000 representative sessions, including word-score payloads; compare query counts, materialized rows, CPU slices and navigation frames. Verify daily boundaries, midnight/timezone changes, restored totals and exact japa accounting.

### UI-05 — Cached Messages hidden behind server fetch

[TeachingRepository.kt](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingRepository.kt), lines 87–104, calls `get(Source.SERVER)` within an 8,000 ms timeout before attempting `Source.CACHE`, for both feed pages and detail. [MessagesViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesViewModel.kt), lines 40–62, waits for that result before publishing loaded content. A fresh page entry can therefore wait near the timeout even with usable cached content. Failure may also return earlier.

The singleton first-page cache only returns immediately inside its 10-second refresh cooldown; after that, a newly created destination again waits for fetch completion. These suspending requests need not freeze the main thread to make navigation feel slow.

**Change:** publish validated cached/in-memory content immediately and refresh in the background. Retain existing content during refresh. Use an inline refreshing/offline state. Continue validating expiry, publication status, schema and locale, and remove stale content when the server reports removal. Keep the existing pagination and request de-duplication.

Home reflection already attempts cache first and is outside the dashboard's loading combine; it should not be blamed for an 8-second Home gate.

**Verify:** previously opened feed/detail with a blackholed network, offline cache miss, content expiry/removal, repeated navigation after the cooldown, and load-more failure. Measure tap-to-readable-cached-content separately from refresh completion.

### UI-06 — Account and reminder work extends the entry gate

**Account-sharing portion resolved in step 05:** [implementation, gate regressions and device counts](step-05-account-state.md). One resolved account producer serves startup and other consumers; unresolved, recovery and child-setup gates remain explicit. Ancillary startup/reminder work remains open for steps 10/11. The following paragraphs retain the original evidence.

[FirebaseAuthRepositoryImpl.kt](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirebaseAuthRepositoryImpl.kt), lines 74–98 and 401–422, loads the Firestore user document before emitting the account, with a 4,000 ms timeout around default-source `get()`. A stalled request can consume that budget before fallback. It is not a guaranteed four-second wait; cached/default-source behavior can finish earlier.

[MainViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/MainViewModel.kt), `openStartupProfile`, awaits [SyncSignedInUserUseCase.kt](../../core/data/src/main/java/com/mantrasiddhi/data/session/SyncSignedInUserUseCase.kt). That bridge performs profile writes, DataStore writes, session login, `scheduleAll()` and a recovery request before the gate becomes Authenticated. [MantraSiddhiApplication.kt](../../frontend/android/src/main/java/com/mantrasiddhi/MantraSiddhiApplication.kt) also schedules reminders at startup. Both calls use the scheduler's mutex, potentially serializing duplicate work.

`SandhyaScheduler.scheduleAll()` loops over periods/stages, performs alarm-service calls and, when enabled, reads logs/calculates future times. It does not itself switch dispatchers. The application launches it on Default, while the profile bridge is invoked from a Main ViewModel coroutine. [JapaCloudSync.requestRecovery](../../core/data/src/main/java/com/mantrasiddhi/data/sync/JapaCloudSync.kt), lines 66–72, schedules recovery; it does not wait for a cloud restore. Do not describe this as synchronous cloud-history download.

The account flow is cold, and Main subscribes twice, for entry routing and child-goal notifications. Each collection installs an auth listener and resolves the account again.

**Change:** publish a shared, UID-scoped account state and use a previously validated local profile for returning-user routing where current identity/setup rules permit. Refresh remote profile fields separately. Preserve recovery, child PIN setup, account ownership and sign-out behavior; never infer authorization merely to make startup faster. After mandatory local association/session establishment, coalesce reminder re-arming in a lifecycle-safe background job. Preserve durable recovery scheduling without making restored content a first-frame prerequisite.

**Verify:** returning profile with slow network, missing profile, Google recovery, incomplete child setup, account switch during startup and reminder correctness. Trace account resolution, local association and scheduling separately.

### UI-07 — Synchronous notification maintenance on launch/resume

[TeachingNotifications.kt](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingNotifications.kt), lines 50–102 and 128–148, performs preference scans and `commit()`. `schedulePending()` calls `pending()`, which parses pending JSON and calls `commit()` even when no removals were needed. `start()` calls `refreshPermission()` (which schedules pending work), then schedules again. [MainActivity.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/MainActivity.kt) calls `refreshPermission()` on resume and synchronous permission bookkeeping before `setContent` on first request. Registration lazily reads/writes an installation ID file from its completion path.

**Change:** coalesce permission/token registration and queue cleanup; run file and preference maintenance on IO, only write actual changes, and keep UI state publication cheap. Retain durable “seen before posting” ordering for notification de-duplication rather than replacing every `commit()` with `apply()`. Measure first-launch prompt interruption separately from actual frame stalls.

**Verify:** debug StrictMode and Perfetto on launch/resume/toggle, with a populated notification queue. Confirm alerts remain exactly-once under the existing policy and the permission state remains accurate.

### UI-08 — Home waits for unused child analytics

[GetDashboardStatsUseCase.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/GetDashboardStatsUseCase.kt), lines 51–84, combines each linked child's progress before emitting the parent's dashboard. Each [GetChildProgressUseCase](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/GetChildProgressUseCase.kt) loads full child sessions and targets. The outer `flatMapLatest` rebuilds child subscriptions when parent inputs change.

`HomeViewModel` copies `childrenProgress` to `HomeUiState`, but current `HomeContent` never reads it. This work has no visible Home benefit and delays its first state until child flows emit.

**Change:** make the Home query contain only data Home displays. Keep child analytics on the Family/Child Progress routes, or add them as an independently loaded optional section if the product needs them later.

**Verify:** parent profiles with 0, 1 and several children; confirm first Home state requires no child-history queries and child pages retain correct results.

### UI-09 — Retained pages can keep processing updates

[HomeViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt), lines 53–92, launches an unconditional collection in `viewModelScope`. It continues until the ViewModel is cleared, regardless of Home visibility. Several screens use `collectAsState` rather than lifecycle-aware collection. The bottom bar's [FloatingNavigationBar.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/FloatingNavigationBar.kt), lines 45–54, already requests `saveState`, `restoreState` and single-top navigation; retained ViewModels can therefore preserve both useful state and ongoing observers.

**Change:** use lifecycle-aware screen collection and `stateIn(WhileSubscribed(...))` for expensive screen-only flows. Merely changing the composable collector will not stop an unconditional ViewModel `launch`. Keep session, notification and sync observers active only as required by their own lifecycle contracts. Cache small derived state for fast return, scoped by user and cleared on account changes.

**Verify:** visit all tabs, leave Home, record a session, and inspect active collectors and CPU work; then return and check restored scroll/state. Do not assume all destinations are recreated on every tap.

### UI-10 — Perceived motion time and missing performance gates

[MantraNavGraph.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/MantraNavGraph.kt), `enterScreenTransition`/`exitScreenTransition`, specifies 320 ms slides for tab switches and 220 ms for detail navigation. The indicator also animates for 320 ms. Both outgoing and incoming content participate during transition. This is intentional visual duration, not evidence that taps are ignored for 320 ms; synchronous work during it can make it visibly stutter.

**Change:** first remove measured blocking work; then compare a shorter transition or less full-screen movement, including reduced-motion behavior, with product review. Do not use animation changes to conceal slow content readiness.

The inspected source/build configuration has no app-owned Macrobenchmark, Baseline Profile generator, or fully-drawn signal. Release already enables R8 and resource shrinking; recommending that they simply be enabled would be redundant. Libraries can contribute profiles, so absence of an app-owned profile does not prove the final package contains none.

**Change:** add benchmark journeys and a Compose-aware fully-drawn signal tied to usable Home/Welcome, followed by an app-generated Baseline Profile for startup and common navigation. Android explains [startup/full-display metrics](https://developer.android.com/topic/performance/vitals/launch-time), [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics), and [profile generation](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile).

## Implementation sequence

1. Capture a physical-device release baseline. Instrument usable-content readiness, image requests and expensive data transformations. Keep traces and build/commit details.
2. Remove the artificial splash wait, add sized async artwork, and make Home sections lazy. These directly address startup and visible transition work without changing accounting.
3. Apply the [lazy-loading plan](lazy-loading.md): make Messages cache-first, load Progress tabs and child details on demand, remove unused child work from Home, and separate the mandatory entry gate from ancillary scheduling. Give independently loaded sections their own loading/error state.
4. Replace repeated history scans with shared/aggregate flows and paged History; scope UI collection to visibility. Preserve accounting semantics with existing and focused regression tests.
5. Move/coalesce notification maintenance, evaluate navigation motion, generate the app Baseline Profile, and introduce regression budgets.

Each stage should be independently measurable and reversible. Image generation should remain a build/development operation, never a first-launch task.

## Device validation and acceptance

Use an optimized, non-debuggable, profileable benchmark variant based on release, with a controlled compilation state and test accounts. Compare the same physical device, density, refresh rate, OS, thermal state, account fixture and build options. Run at least 20 repeated measurements per startup/navigation scenario; report median and p95 along with raw traces, and use longer frame samples for scrolling.

| Journey | Variants | Record |
| --- | --- | --- |
| Launcher → usable Welcome/Home | Cold, warm, hot; signed out/returning account; slow network | TTID, usable-content TTFD, end-to-end launch time |
| Home → each tab → Home | First visit/revisit, rapid taps | Tap-to-first-destination-frame, tap-to-usable-content, frame timing/overrun |
| Home and Messages scroll | Cold/warm images, light/dark, large fonts | Janky frames, decode/upload slices, memory and cache hits |
| Progress overview/history | 0/1k/10k sessions | Query count/rows, CPU aggregation, allocations, page latency |
| Messages feed/detail | Warm cache, offline miss, stalled network, expiry | Cached content latency and independent refresh latency |
| Account/reminder paths | Parent/child, switch, recovery, reminders enabled | Entry-gate stages, correct identity and scheduling |

Proposed initial targets, to calibrate after baseline: no artificial launch delay; cached page content within 250 ms at p95 on the agreed reference device; no network completion required for readable cached Messages; no full-history data materialization merely to show Progress overview; no large synchronous bitmap decode during page composition. At 60 Hz the frame interval is approximately 16.7 ms, and at 120 Hz approximately 8.3 ms: use frame-overrun/jank measurements rather than treating one fixed interval as valid on every device. Aim for fewer than 5% janky frames in the defined navigation/scroll journeys and ratchet budgets against a stable device baseline.

Do not use the splash's first displayed frame as “application loaded”. Keep cold-cache and warm-cache image results separate, and report decoded memory independently from compressed APK/AAB size.

Existing [recitation performance work](../../docs/recitation-performance.md) already moved scoring and cloud acknowledgement off the interaction path and bounded recent-session consumers. This audit targets remaining startup/page-loading paths; those earlier fixes should be preserved.
