# Polling, timers and CPU demand

**Final verification:** see the [step-13 finding disposition and evidence](step-13-verification.md). Original findings below describe the pre-fix code; unresolved release/acceptance gates are tracked explicitly in the final verification.

Reviewed 12 September 2026. Companion to the [database audit](database-audit.md). Frequencies below come from source code; they are not measured CPU consumption, battery drain or wake-lock activity. Coroutine `delay` suspends between checks, so reducing a timer matters mainly when it avoids expensive work on each tick or unnecessary background resumptions.

**First remove repeated alarm re-arming and full-history capture. Then narrow the remaining timers to their actual deadlines and visible consumers.** Do not remove speech/playback safety timing merely because it uses a loop.

## Inventory

| Path | Current trigger | Work performed | Recommended treatment |
| --- | --- | --- | --- |
| [SandhyaReminderSettings](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SandhyaReminderSettings.kt), around line 74 | Every 60 seconds while composed, plus resume and settings changes | Calls `refreshReminders`; [SettingsViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/SettingsViewModel.kt) writes user preference and runs `scheduleAll` | P1: separate status reading from scheduling; re-arm only on relevant changes/recovery |
| [SandhyaViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/sandhya/SandhyaViewModel.kt), lines 61–76 | 30-second clock combined with logs/counts/draft/selected date | Rebuilds full state, solar times, adherence and `scheduler.upcoming` | P1: cache calendar computation; update at next relevant event/day boundary |
| [CloudSyncCoordinator](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncCoordinator.kt), lines 41–75 | 750 ms invalidation debounce; one foreground capture after 1.5 seconds; pending-work retry loop | Broad capture; queue checks; sync/restore | P1: changed-record capture and reliable failure backoff; retain idle stopping |
| [CloudSyncRunner](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncRunner.kt), lines 47–58 | Foreground/job/manual requests | Full local capture **before** pending check, rate-limit claim and network check | P1: throttle/coalesce repair work as well as network work |
| [MessagesScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesScreen.kt), line 83 | Every 30 seconds while composed | Refilters all loaded messages/detail by expiry; updates UI time | P2: schedule next actual expiry and refresh on resume |
| [ChildFamilyQr](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/family/FamilyQrContent.kt), lines 36–41 | Every second until invitation is used/expired | Refreshes expiry boolean; label is static lifetime/used/expired, not a ticking countdown | P2: one expiry deadline plus invite-state updates |
| [BenchmarkAudioPlayer](../../core/data/src/main/java/com/mantrasiddhi/data/audio/BenchmarkAudioPlayer.kt), lines 181–208 | Every 50 ms during playback (20 Hz) | Reads position, scans word timestamps, updates progress and stops segment | P2: preserve precise segment stop; narrow UI progress collection and decouple label rate |
| [PracticeModeViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/practice/PracticeModeViewModel.kt), around lines 202–219 | One-second duration display; 200 ms watchdog during recording | Elapsed timer, silence/end-of-speech/maximum-duration checks | Retain safeguards; scope to active recording; consider event-reset deadlines after timing regression tests |
| [RecitationViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/recitation/RecitationViewModel.kt), around lines 499–533 | 200 ms watchdog during listening; 150 ms gap between recognizer sessions | Segmentation/final-result/silence/maximum-session checks and recognizer restart | Retain; ensure provider error loops terminate/back off and stop cancels every job |
| [Home reflection expiry](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt) | One delay to the displayed reflection's expiry | Removes expired reflection | Already a useful deadline-based pattern; refresh time on resume too |
| [BackupViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/BackupViewModel.kt), around line 56 | One delay to end of manual-sync cooldown | Toggles button readiness | Already one-shot; do not replace with frequent polling |
| [MessagesViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesViewModel.kt), around line 35 | One delay after refresh cooldown changes | Toggles refresh availability | Already one-shot |
| [CloudSyncScheduler](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncScheduler.kt) | Persistent network-constrained one-shot jobs with 60-second latency/backoff | Runs pending sync and rearms while work remains | Keep durable background execution; deduplicate against foreground requests and cancel idle jobs |

## POLL-01 — A “refresh” must not rewrite alarms every minute

**Step 11 resolved locally (12 September 2026):** [implementation, work counts, tests and limits](step-11-event-deadlines.md). The original evidence below describes the pre-fix behavior.

The reminder settings timer calls a method that schedules all periods/stages, rather than simply checking permission/status. With both preparation and event reminders enabled there are six combinations; the scheduler can read logs, calculate upcoming times and issue alarm-service calls for each one. Resume and the initial effect can request this again.

Expose cheap notification-permission and next-reminder state independently. Re-arm on changed region/timezone, reminder preferences, profile ownership, observance, boot/app update or an explicit repair action. Coalesce simultaneous requests by profile and settings revision. Keep the cold-start backstop, but avoid duplicate startup scheduling across Application and profile initialization.

Test that leaving Settings open with no changes causes no periodic preference writes or alarm replacements, while every relevant event still updates the correct alarms.

## POLL-02 — Sandhya clock ticks invalidate too much work

**Step 11 resolved locally (12 September 2026):** [implementation, work counts, tests and limits](step-11-event-deadlines.md). The original evidence below describes the pre-fix behavior.

Every 30 seconds, `buildState` calls `scheduler.upcoming`. [SandhyaScheduler.next](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaScheduler.kt) loads all logs for a user on each enabled period/stage calculation and can examine multiple future dates. This repeats even when the clock changed but all history/preferences stayed identical. With six enabled combinations, one state build can issue six full-log reads. Typing a persisted draft can trigger the same build as well.

Separate:

- Calendar/solar results keyed by date, region and timezone.
- Observance/quantity/adherence projections keyed by relevant data changes.
- Next-reminder status keyed by settings, observed periods and the next deadline.
- Editor text/draft state, which should not recompute solar times or scheduling.

Read logs once into a bounded/shared projection, then reuse them for each period/stage. Schedule one update for the next displayed status transition or midnight. Re-evaluate on resume and wall-clock/timezone changes. `WhileSubscribed(5_000)` already exists upstream, but ordinary `collectAsState` can keep collecting while the Activity is stopped and composition remains alive; use lifecycle-aware collection as well.

## POLL-03 — Capture is outside the sync throttle

**Retry/capture-gate portion resolved in step 07:** [implementation and verification](step-07-discovery-retries.md). Transactional changed-record capture remains step 08. Original evidence follows.

The usual foreground sync loop suspends while idle and obeys a one-minute attempt gate; it is not an always-running one-minute network poll for every account. However, `CloudSyncRunner` runs `capture(uid)` before claiming the attempt. Rejected, offline or redundant requests can still scan/serialize the full local history. A 750 ms debounce merges bursts but does not make each capture cheap, and continuous invalidations can postpone that debounced capture until a quiet period.

There is also a conditional tight-retry path: the coordinator clamps an overdue wait to 1 ms. If capture throws **before** `claimSyncAttempt`, the failure handler marks restore pending but does not advance the persisted next-attempt deadline. The loop can repeatedly request failing captures with only the 1 ms floor plus execution time between them. Normal transport failures occur after the claim and have a deadline; this concern specifically affects pre-claim failures, such as a persistently unserializable row. It is a source-traced failure path, not a measured 1,000-requests/second result.

Persist transactional dirty-record events, and run full repair scans only on bounded bootstrap/repair triggers. Establish a backoff covering the whole attempt, including capture failure. Use a bounded retry policy with jitter for repeated failures and a visible repair state for malformed data; do not discard unsynced practice. Keep the mutex and duplicate-request suppression. Inject a capture failure in a scheduler test and assert attempt frequency remains bounded.

The [remote-only discovery gap, DB-05](database-audit.md) needs an explicit refresh/change signal. Reducing local polling must not make synchronization correctness depend on the user recording new japa on the receiving device.

## POLL-04 — Replace expiry polling with deadline updates

**Step 11 resolved locally (12 September 2026):** [implementation, work counts, tests and limits](step-11-event-deadlines.md). The original evidence below describes the pre-fix behavior.

The Messages 30-second timer does not fetch the server, but repeatedly filters every loaded item and can leave an expired message visible until the next tick. The QR loop checks every second although its label displays no live countdown. A five-minute unused invitation can therefore cause approximately 300 checks for a single visible state transition. Its bitmap generation is already keyed by token/active state and runs off Main; do not claim it regenerates every second.

Schedule a single cancellable delay to the nearest expiry, then recompute the next deadline after content/invite changes. Refresh on resume so expiry during background suspension is handled immediately. Include any future visibility-start boundary if content policy introduces one. Scope timers to visible UI and recalculate after wall-clock changes; local timers do not supersede server-side QR expiry checks.

## POLL-05 — Narrow playback updates before reducing timing precision

**Step 12 resolved locally:** [playback changes, precision regressions and physical-device measurements](step-12-rendering.md). Cosmetic position now publishes nominally every 200 ms and UI reads are narrow; the 50 ms word/segment checks remain. Hidden Practice pauses playback. The following is the original audit evidence.

The playback tracker is Main-scoped and publishes position 20 times per second. [PracticeScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/practice/PracticeScreen.kt) collects position near its root, broadening the scope of frequent state reads. Move those reads into the audio progress control and word-highlight component; publish word changes only when the index changes.

Keep segment-stop precision separate from cosmetic position labels. A coarser label update may be acceptable, while stopping a selected word needs the current precision or a separately scheduled boundary. Retain cancellation on pause/completion/release and verify behavior when navigating away. Measure recomposition/frames before changing the sampling rate.

## POLL-06 — Lifecycle and clock semantics

**Step 11 resolved locally (12 September 2026):** [implementation, work counts, tests and limits](step-11-event-deadlines.md). The original evidence below describes the pre-fix behavior.

**Sync-clock portion resolved in step 07:** [persisted elapsed deadlines and clock/reboot tests](step-07-discovery-retries.md). UI lifecycle ownership is implemented in [step 09](step-09-visible-demand.md); calendar/expiry deadline changes are implemented in [step 11](step-11-event-deadlines.md).

`LaunchedEffect` follows composition lifetime, not automatically the visible/resumed lifecycle. Use lifecycle-scoped collection/timers for UI-only jobs. Similarly, replacing a composable collector cannot stop an unconditional `viewModelScope.launch` subscription. The previously documented child-detail collector leak and repeated cold auth subscriptions increase work without explicit polling.

Persisted sync timestamps use wall-clock time. A backward clock adjustment can extend a cooldown; forward adjustment can make it immediately due. Use a monotonic clock for in-process elapsed deadlines, while retaining a reboot-safe persisted schedule with sanity bounds/reconciliation. Calendar dates and server expiry timestamps still require wall time. Android documents the distinction in [SystemClock](https://developer.android.com/reference/android/os/SystemClock).

## Loops that are not status polling

Room observable queries and Firestore snapshot listeners are event-driven subscriptions. Their cost comes from duplicate collectors, broad invalidations, initial reads and payload processing; do not convert them into polling. Lifecycle scope and query projections are the appropriate tools.

AudioRecord/AudioTrack streaming loops perform required IO. FFT/pitch/media-decoder loops perform bounded signal work. Restore's `while` loops page server results, and backup capture's loops page local rows: their total work and indexing matter, but they are not fixed-frequency status checks. Child-code allocation retries are bounded collision handling. Splash, transient visual feedback and cooldown delays are one-shot timers. Keep these distinctions in the performance inventory.

## Acceptance checks

Use a physical release/profileable build with Perfetto CPU scheduling, main-thread traces, database query tracing and counters for alarm writes, sync attempts and active subscriptions. Capture idle Home, idle Settings, idle Sandhya, an unused QR, background/resume, offline pending sync and active audio separately.

Expected behavior after implementation: no minute-by-minute alarm re-arming on unchanged Settings; no full-log scan solely for a Sandhya clock tick; no UI expiry timers while stopped; bounded retries even when capture fails; no lost sync events; exact midnight/expiry refresh; no regression in speech finalization or selected-word stopping. Report CPU time and work counts before/after on the same fixture. Fewer timers alone is not a sufficient success metric.
