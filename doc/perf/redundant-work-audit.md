# Redundant work and rendering audit

Reviewed 12 September 2026 against source commit `5c5aaba`. This extends the [UI audit](README.md), [database audit](database-audit.md), [lazy-loading plan](lazy-loading.md) and [polling audit](polling-audit.md). No application code, assets, databases or deployed services were changed.

**The strongest findings are duplicated account loading, repeated full-history processing, work retained by hidden screens, and repeated startup/refresh side effects.** Rendering also has avoidable high-frequency state reads and 16 independent shimmer animations on one loading screen. There is no device trace proving that the entire application is painted twice.

## Scope and interpretation

Traced startup and navigation ownership; Main, Home, Progress, Family, child detail, Practice, Recitation, voice-model gating, Settings/backup and Sandhya; their repository/DAO dependencies; notification registration; sync capture and projection; shared artwork, skeletons and charts. Searches were restricted to production source when identifying callers, excluding generated `bin/` copies and tests. Also checked existing guards, sharing, caching and cancellation before classifying work as redundant.

`adb devices -l` returned no connected device during this follow-up. Findings below are source-confirmed paths, with conditional cases identified explicitly. Subscription and animation counts are structural counts, not measured network requests, recompositions, GPU passes or CPU percentages. The earlier database audit contains synthetic query evidence; it does not measure rendering. No additional application tests were run for this documentation change; the earlier SDK/dependency test blockers still apply.

Composition, layout and drawing are separate phases. A composable running again does not prove every child was laid out or every pixel repainted: Compose can skip phases and unchanged children. The useful question is which state change forces which work. See Android's [Compose phases](https://developer.android.com/develop/ui/compose/performance/phases).

## Priority overview

P1 means address early because work scales with subscribers/history or occurs on frequent actions. P2 means a narrower improvement. P3 means cleanup or profiling candidate. These priorities describe likely avoidable work, not measured severity.

| ID | Priority | Finding | Trigger / confidence |
| --- | --- | --- | --- |
| RW-01 | P1 | Account flow creates a listener and profile load per subscriber | Confirmed: two Main subscriptions, five more in Family, another on each correct recitation |
| RW-02 | P1 | Progress reads the same history through six branches and computes unused results | Confirmed while Progress state is collected |
| RW-03 | P1 | Home computes unused child details and rebuilds their subscriptions | Confirmed for a local parent with children |
| RW-04 | P1 | Selecting child details accumulates collectors | Resolved in step 04; counted unit regressions and three physical-device UI tests pass |
| RW-05 | P1 | Screen-owned work outlives visible screens; child goals have two consumers | Confirmed ownership structure; retained lifetimes depend on navigation |
| RW-06 | P1 | Target reads scan the ledger; projection rewrites unchanged goals | Confirmed; second repair pass occurs when the first changed entries |
| RW-07 | P1 | One durable save can lead to several broad backup passes | Confirmed independent triggers; number/timing varies |
| RW-08 | P2 | Voice-model initial and resume checks cancel/restart each other | Confirmed call sites; overlap depends on callback timing |
| RW-09 | P2 | Notification refresh repeats registration and maintenance | Confirmed on startup/resume; successful remote writes depend on connectivity |
| RW-10 | P2 | Reminder and calendar work repeats without relevant changes | Confirmed; detailed timer inventory is in polling audit |
| RW-11 | P2 | Playback position is read high in the UI tree at 20 Hz | Confirmed state-read scope; actual skipped/recomposed children need profiling |
| RW-12 | P2 | Progress loading runs 16 independent shimmer animations | Confirmed construction and composition-phase animation reads |
| RW-13 | P2 | Backup status changes rebuild all conflict reviews | Confirmed even while the review dialog is closed |
| RW-14 | P3 | Layered backgrounds and chart animations are rendering candidates | Overlapping layers/restarts exist; wasted GPU work is not measured |

## RW-01 — Share account observation before the expensive work

**Evidence:** [FirebaseAuthRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirebaseAuthRepositoryImpl.kt), `currentAccount` around line 75 and `loadAccount` around line 401; [AuthUseCases](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/AuthUseCases.kt); [MainViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/MainViewModel.kt); [FamilyViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/family/FamilyViewModel.kt), lines 71–105.

`currentAccount` is a cold `callbackFlow`. Each collection installs a Firebase auth listener, subscribes to `accountChanges`, and calls `loadAccount` for the signed-in user. That performs `users/{uid}.get()` with a four-second timeout and possibly a local profile lookup. The repository being a singleton does **not** share this work.

Main collects it twice: startup routing and goal notifications. Family adds five independent account collections: identity, linked children, pending requests, own goals and linked parents. With both ViewModels alive, that is seven application-level account subscriptions. An account-change publication fans out to all seven. Firebase may share transport/cache internally; seven subscriptions are not evidence of seven billed reads, but the app independently requests and processes them.

[IncrementActiveCloudGoalUseCase](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/GoalCloudUseCases.kt), line 59, also calls `currentAccount.first()`. [RecitationViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/recitation/RecitationViewModel.kt), lines 562–579, invokes it after every correct NORMAL evaluation. Even a parent or individual loads the account before discovering that no child-goal work is needed. Interactive sign-in paths additionally call `loadAccount` directly while auth listeners publish the same identity.

**Change:** maintain one application-scoped, account-aware state producer, sharing *after* profile resolution. Represent unresolved identity separately from signed out; cancel old-UID loads and clear replay on sign-out. Let consumers select the UID/role fields relevant to their subscriptions, with equality filtering before switching child/parent queries. Coalesce explicit refreshes with an in-flight load while retaining deliberate refresh after profile/role changes. A local cached role must never authorize a different UID or bypass server rules.

For recitation, use the verified account associated with the saved event rather than starting account I/O per chant. Coordinate this with DB-02's transactional cloud-event fix; simply dropping the cloud call would lose promised counts.

**Verify:** count auth-listener attachment, `loadAccount` entry and profile `get()` invocation on startup, Family entry, account refresh and 20 correct attempts. Family should not multiply account producers. Sign-out/account switch must immediately clear old data; cold startup must retain the recovery and child-setup gates.

## RW-02 — Progress computes results it then replaces

**Evidence:** [ProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressViewModel.kt), `combineCore`, `combineTrends` and top-level `combine`; [RecitationRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/repository/RecitationRepositoryImpl.kt), `getSessionsForUser`; [JapaHistoryRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/repository/JapaHistoryRepositoryImpl.kt).

One Progress subscription starts full-session flows for overview, daily counts, accuracy, history and axis trends, plus a sixth raw-session query in the japa fallback. The first five independently map Room entities to domain objects. Overview makes several passes and date conversions; daily/accuracy/axis use cases scan the session list for each chart day. The repository's `flowOn(IO)` covers its own mapping, not automatically every downstream use-case calculation.

There are two particularly clear discarded results:

- When cloud summary exists, `combineCore` still collects and computes all three local fallback outputs, then replaces them with cloud values. It also waits for their initial emissions.
- The outer combine always replaces `DailyCount.count` with japa totals, whether the earlier count came from local audio or cloud audio. The prior audio counting work only provides a date/label scaffold that can be generated without scanning audio history.

The selected tab lives only in [ProgressScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressScreen.kt). The UI renders one tab, but all data branches remain active. Multiple branches responding to one transaction can also produce successive intermediate combined states; exact emission/recomposition counts depend on scheduling and equality.

**Change:** generate date buckets independently, load only selected-tab projections, and switch to local fallback only when needed. For overlapping consumers, share a bounded, mapped snapshot or common aggregate producer *after* expensive conversion. Avoid making a permanent process-wide full-history cache. Move remaining large pure calculations off Main. Keep legacy audio fallback until all historical sessions are represented in the ledger.

**Verify:** count queries and entity conversions per tab and per saved attempt. With a valid cloud summary, the local fallback analytics branches should be inactive. Compare every displayed total/date/score before and after, including missing cloud data, manual japa, timezone changes and old audio-only history.

## RW-03 — Home builds child data that its UI never reads

**Evidence:** [GetDashboardStatsUseCase](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/GetDashboardStatsUseCase.kt), lines 59–99; [GetChildProgressUseCase](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/GetChildProgressUseCase.kt); [HomeViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt); [HomeScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt).

Home carries `childrenProgress` into its Success state but never reads it in the current screen. For a local parent with children, the use case nevertheless subscribes to every child's targets and full session history and looks up each child's profile.

The child combine is constructed inside a `flatMapLatest` driven by the parent's targets, sessions and child list. A parent session/target emission cancels and rebuilds child subscriptions even if child identities are unchanged. Each target subscription also runs the read-time repair in RW-06. Parent data therefore triggers child reads and repair checks for no visible Home output.

Home also computes an audio `todayCount` that the current ViewModel's non-null `todayJapa` always replaces in the display. Full audio history is still used for streaks, so removing only that count does not eliminate the history query.

**Change:** use a Home-specific projection without child history; keep detailed child computation in the child screen. Where child summaries are needed, switch their subscriptions on distinct child IDs rather than unrelated parent stats. Preserve streak semantics and the independent japa total.

**Verify:** opening Home and saving a parent attempt should create no child-history subscriptions for this screen. Opening child progress must still show the same children and correct totals.

## RW-04 — Repeated child selection leaves earlier work running

**Resolved — step 04, 12 September 2026.** One selected-child `flatMapLatest` pipeline cancels on replacement, close, screen disposal and account change. Request/account/child identity guards reject stale results, and account observation survives overview errors. Counted regressions establish one detail pipeline at a time and none after close; 88 app unit tests and three Pixel 7 Pro UI tests pass. [Implementation and verification evidence](step-04-child-details.md). The following describes the original finding; overview-wide work remains step 09.

**Evidence:** [ChildProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/childprogress/ChildProgressViewModel.kt), `selectChild` around line 134 and `clearSelectedChild` around line 157.

Every selection launches a new untracked collecting coroutine combining child progress and weak words. Clearing the detail only sets Idle. Selecting A, B, A therefore creates three detail pipelines until the ViewModel is cleared, in addition to the overview's child pipelines. Old emissions can overwrite the visible child or reopen detail state after closing it. Repeatedly selecting the same child duplicates the same work.

**Change:** keep one nullable selected-child identity and use `distinctUntilChanged`/`flatMapLatest`, or explicitly cancel/join the previous detail job. Clear selection when closing and on account changes. Reuse an already available compact summary where appropriate; weak-word/history work should be owned by the selected detail. Family's existing selected-child switching demonstrates the intended pattern.

**Verify:** rapidly select A → B → A → close, then update both children. At most one detail pipeline may exist, and none after close; old results must never replace current selection. This is also a correctness fix, not just a CPU optimization.

## RW-05 — Screen lifetime and producer lifetime are mismatched

**Evidence:** direct `viewModelScope.launch { ...collect... }` in [HomeViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt), [FamilyViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/family/FamilyViewModel.kt), [TargetsViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/targets/TargetsViewModel.kt), [PurashcharanaViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/purashcharana/PurashcharanaViewModel.kt) and child progress. [FloatingNavigationBar](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/FloatingNavigationBar.kt), lines 48–55, saves/restores destination state.

These producers are canceled when their ViewModel clears, not when their screen stops being visible. Detail navigation keeps the underlying entry, and saved tab entries can retain ViewModels. Visiting several screens can leave several pipelines reacting to the same database invalidation. Switching a UI collector alone does not stop a ViewModel's unconditional init job.

Screens generally use `collectAsState`, including Progress's `WhileSubscribed(5000)` producer. A composed but stopped activity can still have a subscriber; a subscriber timeout is not itself a visibility policy. Lifecycle-aware UI collection and subscription-aware production need to work together. See Android's [lifecycle coroutine guidance](https://developer.android.com/topic/libraries/architecture/coroutines).

There is a second duplication for signed-in children: Main's notification observer and Family's `myGoals` observer both call [FirestoreGoalRepositoryImpl.goalsForChild](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirestoreGoalRepositoryImpl.kt). Each builds its own snapshot-flow mapping/sorting pipeline. SDK transport sharing may reduce network duplication, but application processing remains separate. Main's comment says foreground, while its collection has no foreground lifecycle gate.

**Change:** make visual projections subscription-aware and collect them with screen lifecycle. Share account-keyed child-goal data between the required notification consumer and visible Family UI. Give essential delivery/sync a deliberate application/background owner instead of treating all work as screen-owned. Preserve notification baseline semantics: naïvely canceling and restarting the observer can miss new goals or re-notify old ones.

**Verify:** visit all tabs, open a detail, background the activity, then invalidate one session table row. Record active producers and listeners at each stage. After the intended grace period, only visible UI work and explicitly required background work should remain.

## RW-06 — Reading a target can trigger repeated repair scans

**Evidence:** [TargetRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/repository/TargetRepositoryImpl.kt), lines 52–77; [JapaDao](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/JapaDao.kt), `creditUnassignedTargetEntries` and `project`.

Every `getActiveForUser` and every fresh `getAllForUser` subscription calls `refreshGoalCredit`. Even if no repair is needed, it reads the whole ledger into `before`, then `creditUnassignedTargetEntries` reads the ledger again and loads targets. When repair does change entries, `refreshGoalCredit` calls `project`, which invokes `creditUnassignedTargetEntries` again before reading entries/targets for projection. The calls occur in the same outer transaction, so the second repair can reuse knowledge from the first.

`project` filters all entries separately for every target/vow and writes every projection row, including unchanged counts/completion. Those writes create opportunities for table invalidation, other UI processing and another backup capture. Room observable queries can rerun when a referenced table changes, including rows outside the query result; result equality filtering can suppress downstream work, but does not prevent the SQL rerun. See [Room observable queries](https://developer.android.com/training/data-storage/room/async-queries).

**Change:** retain repair correctness while making it event-driven at goal creation, ledger mutation and restore/migration boundaries. If compatibility requires read-time repair, use an explicit dirty/version marker and a shared repair result. Within a transaction, reuse loaded rows and avoid a second unchanged repair pass. Update only projections whose persisted fields changed; group quantities once by allocation. Do not remove same-day unclaimed credit or cap the underlying performed ledger.

**Verify:** repeated reads of an unchanged active target should cause no writes and no full-ledger repair scans after initialization. Tests must preserve overshoot/correction, late goal creation, original allocations and restore behavior.

## RW-07 — Durable saves are followed by broad capture and reserialization

**Evidence:** [RecitationRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/repository/RecitationRepositoryImpl.kt), `saveSession`; [JapaCloudSync](../../core/data/src/main/java/com/mantrasiddhi/data/sync/JapaCloudSync.kt), `enqueue`/`capture`; [PracticeBackupStore](../../core/data/src/main/java/com/mantrasiddhi/data/sync/PracticeBackupStore.kt); [CloudSyncCoordinator](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncCoordinator.kt); [CloudSyncRunner](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncRunner.kt).

A normal save already enqueues eligible changed ledger and session records transactionally. Later, the coordinator's 750 ms invalidation debounce can call full capture; a requested runner captures again before checking whether a network attempt is allowed. Foreground entry adds another capture path. These are independent triggers, so there is no universal exact three-pass count.

Capture calls `enqueue` with an empty before-list, serializes every ledger entry and goal, then pages through **all** sessions/logs/changes from the beginning. Every session looks up its audio entry. Fingerprints prevent unchanged payloads from being enqueued again, but do not prevent those queries, object construction or serialization. The runner lock deduplicates runner execution, not coordinator captures outside that lock.

There is a smaller inner redundancy: `PracticeBackupStore.enqueue` canonical-encodes the new payload, reads the saved fingerprint string, decodes that old string and canonical-encodes it again. Current `putQueued` payload paths already write canonical fingerprints via [BackupFingerprint](../../core/data/src/main/java/com/mantrasiddhi/data/sync/BackupFingerprint.kt). A direct comparison could avoid repeated old-payload parsing after validating/migrating legacy fingerprint formats; do not assume all previously installed versions wrote identical numeric representations.

The compact progress path also calls `buildSeedData` on **every** session even after bootstrap, despite its one-time-backfill description. This reissues stats/best/day-count queries and embeds another seed. DB-07 covers its correctness issues; optimizing seed construction must preserve an exact replay/bootstrap boundary.

**Change:** use transactional dirty-record events as the normal source, coalesce repair capture by account/data revision, and keep full scans for explicit repair/bootstrap. Track updates as well as new IDs so old corrections are not missed. Cache or version canonical fingerprints. Keep upload retry and restore checkpoints durable.

**Verify:** after initial sync, one new session should serialize only its affected records/projections; an unchanged foreground resume should not serialize all history. Count reads/encodes separately from network writes, and test crash recovery, offline retry and corrections to old entries.

## RW-08 — Voice-model initialization and resume can restart the same check

**Evidence:** [VoiceModelViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/voicemodel/VoiceModelViewModel.kt), lines 69–96; [VoiceModelGate](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/VoiceModelGate.kt), lines 45–55; [VoiceModelManager](../../core/data/src/main/java/com/mantrasiddhi/data/speech/VoiceModelManager.kt), `checkStatus`/`querySupport`.

Initialization calls `refresh`; attaching/resuming the gate calls it again. `refresh` cancels the current job and starts over, rather than joining an equivalent in-flight check. If initialization is still checking, work is discarded; if it just completed, the next call repeats it. A check can create/query/destroy speech recognizers for available backends. The manager's mutex serializes checks but does not cache a completed check or merge identical requests.

**Change:** one initial-check owner, or join an existing equivalent check. Keep explicit retry and revalidation after settings/download/provider changes. The process-wide `lastKnownReady` already prevents a spinner flash; it is a presentation hint, not request deduplication. Do not make readiness permanently cached.

**Verify:** count provider creation and support queries when entering Practice/Recitation and resuming from settings. No cancel/restart pair should occur for one unchanged initial-entry event; a real model removal/download must still be detected.

## RW-09 — Permission refresh writes unchanged notification registration

**Evidence:** [TeachingNotifications](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingNotifications.kt), `start`, `refreshPermission`, `register`, `pending` and `schedulePending`; [MainActivity](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/MainActivity.kt), `onCreate`/`onResume`.

`start` ensures the channel, calls `refreshPermission` (which ensures it again and schedules pending delivery), installs an auth listener that registers, then schedules pending delivery again. Activity creation can refresh permission, and every `onResume` refreshes it. Each signed-in refresh requests the FCM token and sets the installation document, including a new server timestamp, without checking whether UID/token/enabled/version/locale changed.

Pending checks traverse preference maps and call `commit`, even when cleanup removes nothing. Reusing the same job ID avoids separate job identities, but repeated scheduling calls can still replace the pending job. Channel creation is idempotent; its repetition is lower impact than repeated profile registration and synchronous preference maintenance.

**Change:** separate permission observation, registration synchronization and pending-delivery scheduling. Coalesce registration by installation/account/token/settings signature, record success only after write acknowledgement, and retain bounded retry/freshness refresh. Use a single startup maintenance pass and skip empty cleanup writes. Keep the intentional commit-before-notification rule that prevents duplicate alerts after a crash.

**Verify:** unchanged resume loops should not each issue a registration write. Token rotation, account switch, opt-out and system permission changes must promptly publish the new state. Offline writes and retries must not mark unsaved state as successfully synchronized.

## RW-10 — Repeated reminder work also has multiple owners

**Evidence:** [MantraSiddhiApplication](../../frontend/android/src/main/java/com/mantrasiddhi/MantraSiddhiApplication.kt), cold-start `scheduleAll`; [SyncSignedInUserUseCase](../../core/data/src/main/java/com/mantrasiddhi/data/session/SyncSignedInUserUseCase.kt), post-login `scheduleAll`; [SandhyaViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/sandhya/SandhyaViewModel.kt), init and `buildState`; [SettingsViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SettingsViewModel.kt), `refreshReminders`.

Application startup, profile association and Sandhya ViewModel creation all schedule reminders. The settings timer does it again every minute. Some transitions legitimately change the active user, so not every repeated call can be suppressed using a global time cooldown.

Sandhya's 30-second clock and draft edits rebuild state that includes solar calculations and upcoming reminders. `buildState` already receives logs but `scheduler.upcoming` obtains log data again for its period/stage calculations. These are repeated computations for mostly unchanged inputs, not six independent new user requests.

**Change:** have one scheduler accept a versioned desired state keyed by user, date/zone, region, preferences and relevant observance. Coalesce identical requests, retain boot/time-change recovery, and make upcoming-reminder projection reuse an appropriate log snapshot. Split draft text from calendar calculations. See [POLL-01/02](polling-audit.md) for details and deadline replacement.

**Verify:** count alarm replacements and log reads for startup, opening settings, draft typing and an idle minute. Distinct-user transitions and actual preference/date changes must still produce correct alarms.

## RW-11 — Playback invalidates a larger UI scope than necessary

**Evidence:** [BenchmarkAudioPlayer](../../core/data/src/main/java/com/mantrasiddhi/data/audio/BenchmarkAudioPlayer.kt), lines 181–208; [PracticeScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/practice/PracticeScreen.kt), lines 67–71 and player arguments; [AudioPlayerBar](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/practice/components/AudioPlayerBar.kt).

The player updates position every 50 ms during playback. Practice reads that state at the screen level and passes a value to the player bar. The bar computes progress in composition and formats a whole-second label although the position changes roughly 20 times per second. Its `LinearProgressIndicator` lambda closes over an already-read `progress`; that alone does not defer the original state read into drawing.

**Change:** isolate playback collection to the player/progress subtree. Read fine-grained progress in the supported drawing/progress lambda, and expose an equality-filtered whole-second value for the label. Keep active-word state independent; equal word indices are already suppressed by StateFlow. Preserve the precise segment-stop logic rather than slowing the audio timer indiscriminately. Android recommends [deferring rapidly changing state reads](https://developer.android.com/develop/ui/compose/performance/bestpractices).

**Verify:** use recomposition counts and a frame trace during playback. The changing progress indicator should not force unrelated practice content through composition. Confirm word highlighting, pause/end, segment playback and accessibility labels still work. Source alone cannot establish how many descendants the compiler currently skips.

## RW-12 — A loading placeholder starts 16 shimmer animations

**Evidence:** [ProgressScreen.ProgressLoadingSkeleton](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressScreen.kt), lines 117–132; [LoadingSkeleton](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/LoadingSkeleton.kt), `shimmerEffect`, `SkeletonCardItem` and `SkeletonSessionItem`.

The screen creates two skeleton cards with two shimmering blocks each, plus three skeleton session rows with four blocks each: **2 × 2 + 3 × 4 = 16**. Each block creates its own infinite transition. `shimmerEffect` reads `translateAnimation.value` inside `Modifier.composed`, constructs a colors list and gradient, and supplies a new brush as the animation advances. This causes recurring composition work while the application is already waiting for data. Sixteen animation instances do not imply sixteen separate hardware timers.

**Change:** share one phase across the visible skeleton group, read it in a draw modifier and cache static geometry/colors with appropriate size/theme keys. A static placeholder is also sufficient where shimmer adds little. Stop animation when the loading state is no longer shown and honor reduced-motion settings. Avoid introducing remembered brushes with stale theme/size values.

**Verify:** count animation owners and composition activity during prolonged loading. Retain skeleton dimensions so content arrival does not jump; inspect light/dark theme and large font layouts.

## RW-13 — Backup status updates rebuild hidden conflict details

**Evidence:** [BackupViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/BackupViewModel.kt), lines 37–53; [BackupStatusContent](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/BackupStatusContent.kt), local `reviewing` flag and first-review dialog.

The content combine takes backup state, pending count, all conflicts and all user entries. A pending-count or backup-status emission reruns the entire conflict mapper even if conflict content did not change. Each conflict linearly searches the entries, decodes two payloads, compares revisions and formats a date: O(conflicts × entries) lookup work before payload processing. It builds every review while the dialog is closed, and the dialog displays only the first one. Even with zero conflicts, the full entries subscription remains active.

**Change:** separate cheap status/count state from selected conflict details. Query only referenced entries, or at minimum build an entry-ID map once per entry revision and share the resulting review projection independently of status changes. Load the chosen review on demand, preserving the review-needed badge and stale-operation protection during resolution.

**Verify:** advancing a pending upload count should cause no conflict JSON decoding or entry scan when conflict content is unchanged. Opening/revising/resolving a conflict must still show the correct current and alternative totals.

## RW-14 — Inspect rendering layers without assuming a double paint

**Evidence:** outer Scaffold in [MantraNavGraph](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/MantraNavGraph.kt); inner Scaffold and full-size same-background Surface in [ProgressScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressScreen.kt), lines 79–109; image/gradient layers in [LotusDesign.AmbientArtwork](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/LotusDesign.kt), lines 59–67.

Several destinations nest a screen Scaffold within the app Scaffold. Progress adds another opaque background Surface. Ambient artwork draws an image and a gradient over it. These are candidates for overlapping drawing, but Compose/display-list/GPU optimizations and clipping determine the actual cost. A gradient over an image is intentional visual design; nested Scaffolds also own different bars/insets. Removing either indiscriminately can break appearance or layout.

[DailyBarChart](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/components/DailyBarChart.kt), [AccuracyLineChart](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/components/AccuracyLineChart.kt) and [AxisTrendChart](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/components/AxisTrendChart.kt) reset progress to zero and run an 800 ms reveal on entry/data change. Switching away and back recreates that reveal because only the selected tab is composed. Equal data keys do not restart an existing effect. For frequently changing data, consider updating from the current chart values instead of replaying the entire entrance. Path/grid/label calculations in draw blocks can be cached if profiling justifies it; the chart data is only seven points and `rememberTextMeasurer` already provides caching.

**Change/verify:** inspect GPU overdraw, Compose tracing and frame timing on representative devices. Remove confirmed redundant opaque fills or cache measured draw work while preserving dark mode, insets, artwork fade and navigation coverage. Incoming/outgoing screens temporarily coexist for the configured navigation animation; that is expected transition work, not proof of an accidentally duplicated screen. Image sizing and eager offscreen Home composition remain higher-confidence targets in the existing [image plan](image-delivery.md).

## Smaller cleanup opportunities

These should follow the larger changes, with no claimed perceptible speedup:

- [TargetsContent](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/targets/TargetsContent.kt), lines 59–63, filters/sorts completed history inside composition. Derive it when relevant target data changes, or remember with all relevant keys.
- [SessionHistoryList](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/components/SessionHistoryList.kt), lines 99–100, recreates a date formatter/text per recomposed row. Cache formatted text by timestamp, locale and timezone; do not share mutable `SimpleDateFormat` across threads.
- [TeachingRepository.fetchPage](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingRepository.kt) sorts by pinned after querying by pinned then publication date. Successful decoding preserves query order, so this extra sort appears removable. Pages are capped at 30, making this a small cleanup; verify cache/server ordering and pinned ties.
- Home's eager scrolling Column composes artwork/cards below the viewport. Converting appropriate sections to keyed lazy items prevents work before it is useful; this is UI-03, not a newly discovered double render.

## Work that should not be classified as redundant

- Multiple `GetCurrentUserUseCase` consumers read [SessionManager](../../core/data/src/main/java/com/mantrasiddhi/data/SessionManager.kt)'s existing StateFlow. They do not each create Firebase account listeners. This differs from RW-01's cold cloud account flow.
- Home calls reflection refresh on composition and resume, but its ViewModel rejects an active duplicate and [TeachingRepository](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingRepository.kt) uses a mutex and ten-second cooldown. Do not report two guaranteed server fetches from those call sites.
- Bottom navigation already guards the selected route and uses `launchSingleTop`/`restoreState`. No unconditional double navigation was found there.
- Cloud runner `tryLock`, payload fingerprints, replay checkpoints and manual-operation identities already prevent important duplicate effects. Optimize their surrounding work without removing those protections.
- Recitation's recent history query is bounded to five. Target snapshots before and after evaluation can serve different completion/feedback purposes; do not delete one solely because the same function appears twice. Remove repeated repair work behind reads instead.
- [BundledMantraCatalog](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/mantra/BundledMantraCatalog.kt) loads definitions through a lazy singleton delegate. Reading words during composition is not evidence of re-reading catalog JSON.
- Family QR bitmap generation is keyed and off Main; the expiry timer does not rebuild the bitmap every second. Speech watchdogs, actual recording, segment-stop timing and durable retry are functional work, as explained in the polling audit.
- A singleton, mutex, `distinctUntilChanged`, `remember`, or lazy UI container alone does not solve every layer: sharing must wrap the expensive producer; equality cannot undo SQL already run; UI laziness cannot bound an eager database query.

## Implementation and measurement order

1. Fix RW-01's shared account producer and RW-04's detail cancellation. Both remove clear duplicate work and reduce race opportunities.
2. Address RW-02/03/05 together: demand-driven screen projections, stable subscription keys and correct lifetime ownership. Check the actual producer count after navigating, not only the visible UI collector count.
3. Coordinate RW-06/07 with the database correctness repairs. Keep save/outbox atomicity, unclaimed-credit semantics, conflict handling and recovery coverage while eliminating broad repair scans.
4. Coalesce voice, notification and reminder refreshes using relevant input identity, freshness and explicit invalidation. A blanket delay can hide required changes.
5. Narrow playback/shimmer state reads, then profile overdraw/charts before simplifying rendering layers.

For each change, capture a baseline and an after trace with the same account, fixture, build type and device. Instrument counts for listener attach/detach, account loads, DAO queries, mapped rows, repair/capture invocations, payload encodes, alarm replacements and registration writes. Use debug-only counters/traces, without recording tokens or personal payloads. Measure frame time, long Main-thread work and allocations separately; use recomposition counts to locate work, not as a standalone success metric.

Required scenarios: cold start; repeated Family/tab/detail navigation; background/foreground; rapid child selection; long-history session save; idle Settings; benchmark playback; prolonged Progress loading; account switch; offline recovery and remote correction. Correctness checks from the database audit remain mandatory when optimizing counting or sync. Success means lower repeated work with unchanged observable behavior, not merely fewer function names in the code.
