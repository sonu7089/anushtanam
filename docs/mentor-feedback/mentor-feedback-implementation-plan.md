# Mentor feedback: implementation and verification

Status: proposed engineering work; source baseline inspected 6 September 2026 and notification/Home integrations reviewed 7 September 2026. Start with the [overview](mentor-feedback-plan.md), [product specification](mentor-feedback-product-spec.md), and [remote publishing plan](remote-content-and-notifications.md).

The paths below describe the current code; names explicitly marked “proposed” describe additions. This task did not run the app, reproduce the phone's reminder defect, or execute implementation tests.

## 1. Current code map

| Workstream | Existing source to inspect/change |
| --- | --- |
| Sandhya model and persistence | [Sandhya.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/model/Sandhya.kt), [SandhyaLogEntity.kt](../../core/data/src/main/java/com/mantrasiddhi/data/local/entity/SandhyaLogEntity.kt), [SandhyaLogDao.kt](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/SandhyaLogDao.kt), [SandhyaLogRepositoryImpl.kt](../../core/data/src/main/java/com/mantrasiddhi/data/repository/SandhyaLogRepositoryImpl.kt) |
| Sandhya UI | [SandhyaScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/sandhya/SandhyaScreen.kt), [SandhyaUiState.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/sandhya/SandhyaUiState.kt), [SandhyaViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/sandhya/SandhyaViewModel.kt) |
| Japa and evaluation | [RecitationViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/recitation/RecitationViewModel.kt), [RecitationScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/recitation/RecitationScreen.kt), [EvaluateRecitationUseCase.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/EvaluateRecitationUseCase.kt), [RecitationSession.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/model/RecitationSession.kt) |
| Goals | [Target.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/model/Target.kt), [IncrementTargetUseCase.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/IncrementTargetUseCase.kt), [Purashcharana.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/model/Purashcharana.kt), [PurashcharanaUseCases.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/PurashcharanaUseCases.kt), [PurashcharanaDao.kt](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/PurashcharanaDao.kt) |
| Reminder scheduling | [SandhyaContract.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaContract.kt), [SandhyaScheduler.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaScheduler.kt), [SandhyaPreferences.kt](../../core/data/src/main/java/com/mantrasiddhi/data/preferences/SandhyaPreferences.kt) |
| Reminder lifecycle/actions | [SandhyaAlarmReceiver.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaAlarmReceiver.kt), [SandhyaBootReceiver.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaBootReceiver.kt), [SandhyaActionReceiver.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaActionReceiver.kt), [SandhyaNotifier.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaNotifier.kt), [MantraSiddhiApplication.kt](../../frontend/android/src/main/java/com/mantrasiddhi/MantraSiddhiApplication.kt) |
| Navigation and entry gates | [Screen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/Screen.kt), [MantraNavGraph.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/navigation/MantraNavGraph.kt), [VoiceModelGate.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/VoiceModelGate.kt), [MainActivity.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/MainActivity.kt), [AndroidManifest.xml](../../frontend/android/src/main/AndroidManifest.xml) |
| Google identity and photos | [AuthRepository.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/repository/AuthRepository.kt), [FirebaseAuthRepositoryImpl.kt](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirebaseAuthRepositoryImpl.kt), [AuthViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/auth/AuthViewModel.kt), [ParentAuthScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/auth/ParentAuthScreen.kt), [AuthAccount.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/model/AuthAccount.kt), [UserProfile.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/model/UserProfile.kt), [SyncSignedInUserUseCase.kt](../../core/data/src/main/java/com/mantrasiddhi/data/session/SyncSignedInUserUseCase.kt) |
| Count sync | [CloudSyncOutbox.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncOutbox.kt), [CloudSyncTransport.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncTransport.kt), [CloudSyncPolicy.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncPolicy.kt), [CloudSyncRunner.kt](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncRunner.kt), [deferred-cloud-sync.md](../deferred-cloud-sync.md) |
| Database and backend | [MantraSiddhiDatabase.kt](../../core/data/src/main/java/com/mantrasiddhi/data/local/MantraSiddhiDatabase.kt), [DatabaseModule.kt](../../core/data/src/main/java/com/mantrasiddhi/di/DatabaseModule.kt), [exported schemas](../../core/data/schemas/com.mantrasiddhi.data.local.MantraSiddhiDatabase), [firestore.rules](../../backend/firebase/firestore.rules), [backend README](../../backend/firebase/README.md) |
| Content and visual review | [strings.xml](../../core/resources/src/main/res/values/strings.xml), [SadhanaWisdom.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/content/SadhanaWisdom.kt), [reference audio](../../core/resources/src/main/res/raw/gayatri_mantra_benchmark.mp3), [DesignReviewActivity.kt](../../frontend/android/src/debug/java/com/mantrasiddhi/review/DesignReviewActivity.kt), [UI review gallery](../ui-review/README.md) |
| Dependencies | [libs.versions.toml](../../gradle/libs.versions.toml), [Android build file](../../frontend/android/build.gradle.kts), [data build file](../../core/data/build.gradle.kts) |
| Existing remote push | [AppFirebaseMessagingService.kt](../../core/data/src/main/java/com/mantrasiddhi/data/remote/AppFirebaseMessagingService.kt), [GoalNotifier.kt](../../core/data/src/main/java/com/mantrasiddhi/data/notification/GoalNotifier.kt), [Cloud Functions entry point](../../backend/firebase/functions/index.js) |
| Dynamic Home integration | [HomeScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt), [HomeViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt); new content repository, cache, renderer, and developer admin workflow are specified in the [remote-content plan](remote-content-and-notifications.md) |

## 2. Count model and transaction boundary — P0

### 2.1 Recommended approach

Add a quantity-based japa contribution model alongside pronunciation sessions. Do not create 600 fabricated `RecitationSession` rows for a 600-count mental session, and do not populate required score fields with invented scores.

Keep `SandhyaLog` as the observance/occurrence record. Its displayed count is an aggregate of linked contributions. Record “count unknown” separately from an explicit zero, and keep logging available even if no notification occurred.

Proposed conceptual entities:

| Entity | Minimum information | Reason |
| --- | --- | --- |
| `JapaEntry` | Stable UUID, local profile ID, stable sync owner/profile identity when linked, `mantraId`, actual whole-number quantity, origin, chanting method if known, practice timestamp, practice date and timezone, optional Sandhya period/occurrence key, optional evaluated-session ID, revision and deleted state | One actual practice contribution independent of any number of goal views. |
| `JapaChange` | Stable operation UUID, entry UUID, expected base revision, replacement values or tombstone, created time, sync state | Idempotent save/correction history and a recoverable basis for conflicts. |
| `JapaGoalAllocation` | Entry UUID, stable goal/vow UUID, eligibility/policy version | Keeps corrections attached to the original applicable goal, including after withdrawal or replacement. |
| `JapaDraft` | Draft UUID, profile/mantra/context, working quantity, already committed quantity if partial saves are supported, update time | Recoverable counter state that does not count as completed japa. |
| Legacy baseline | Stable account/goal baseline identity, quantity, migration provenance/cutover information | Preserves existing aggregates without pretending complete historical event data exists. |

Origin and method answer different questions. Suggested origins: `MANUAL_ENTRY`, `TAP_COUNTER`, `AUDIO_ACCEPTED`, and `LEGACY_BASELINE`. Suggested methods: `MANASIKA`, `UPAMSU`, `VAIKHARI`, and `UNSPECIFIED`. A count typed after a session does not prove which method was used; default mental japa for the new flow but do not invent methods for old data.

Use `Long` quantities for new accounting with explicit overflow checks. Existing short targets use `Int`; handle conversion safely or migrate them deliberately. Add stable goal identities, mantra eligibility, and explicit lifecycle state to the current `Target` contract. The current active-target query relies on `isCompleted = 0`; a correction to an older goal must not make it compete with the current target. A target's size or a vow's mantra/akshara count is not a unique identity.

Store practice date and timezone at entry creation. A later timezone change must not rebucket historical sessions silently. A Sandhya occurrence key should identify owner/profile + practice date + period; local auto-increment row IDs alone cannot be used as cloud identities.

### 2.2 Proposed use cases

- [ ] `RecordJapaUseCase`: validate and commit a completed quantity; return the saved contribution, period/day total, affected goals, and crossed milestones.
- [ ] `ReviseJapaEntryUseCase`: correct an entry using its expected revision; update projections and retain the prior version for audit.
- [ ] `RemoveJapaEntryUseCase`: record a tombstone/reversal with undo support rather than deleting sync identity.
- [ ] `ObserveJapaSummaryUseCase`: expose quantity totals separately from pronunciation-session statistics.
- [ ] `SaveJapaDraftUseCase` / `ResumeJapaDraftUseCase`: manage uncommitted session recovery.
- [ ] An allocation policy shared by manual, counter, and retained accepted-audio counting paths.

Names are suggestions; preserve the repository's frontend/domain/data boundaries. Pure validation, eligibility, and milestone arithmetic belong in the domain; Room transactions and outbox integration belong in data.

### 2.3 Atomic save sequence

For one confirmed operation:

1. Validate ownership, mantra, count, practice date, revision, and goal allocation.
2. In **one Room transaction**, store the entry/change, update observance/count associations and local projections, and insert an account-bound outbox operation if the profile is already linked.
3. Commit, then publish the new state to the UI and request sync/widget refresh outside the transaction.
4. Retry scheduling independently if it fails; the committed data and queued upload must remain recoverable.

Refactor the existing outbox boundary as needed so transaction-safe insertion is separate from scheduling. `CloudSyncOutbox.enqueue` currently includes a scheduler call. The existing recitation save, progress enqueue, target increment, and Purashcharana increment are separate operations; copying that sequence for editable bulk counts would leave a process-death gap. See the limitation already recorded in [deferred-cloud-sync.md](../deferred-cloud-sync.md).

For a local-only profile, save durably without a Firebase UID and defer association to an explicit one-time account-import operation. Do not assign whatever account happens to be signed in later without verifying the profile association.

### 2.4 Projection rules

```text
performed japa = sum of current, non-deleted contribution quantities
                + only those legacy baselines not already represented by entries

eligible goal total = goal-specific legacy baseline
                      + sum of eligible contributions allocated to that goal

displayed goal count = min(target, max(0, eligible goal total))
remaining           = max(0, target - eligible goal total)
crossed milestones  = milestones where previous total < milestone <= new total
```

Never add the personal-target total and Purashcharana total to compute performed japa. Keep the uncapped eligible total so a correction after an overshoot behaves correctly: a 500 target with 740 eligible japa still displays 500 after a correction to 640.

- [ ] Replace one-at-a-time quantity assumptions in `IncrementPurashcharanaUseCase`, repository/DAO logic, and target calculations for new entries.
- [ ] Replace `landsOnMilestone` equality checks with before/after crossing checks for batches.
- [ ] Recompute count-based completion timestamps/state after corrections; preserve explicit withdrawal state and historical celebration events.
- [ ] Link each newly accepted counted-audio session to at most one japa entry. Transition its old direct increment path so both paths cannot credit the same session.
- [ ] For editing a period's absolute total, calculate the difference from its current total atomically. Attribute adjustments to editable manual entries with a visible history; if the requested total is below linked audio contributions, explain which records need correction rather than silently rewriting assessed sessions.
- [ ] Update daily/lifetime japa and goals together. Leave word mastery, accuracy, transcription, and learning-session counts on their existing assessment data.

## 3. Migration and synchronization — P0 foundation, P1 restore UX

### 3.1 Room upgrade

Current source declares **database version 9**. Allocate the next version at implementation time; if no other migration lands first, this is v9 to v10.

- [ ] Add new tables, indexes, stable identities, optional photo fields, and target/mantra fields through non-destructive migrations.
- [ ] Preserve users, sessions, existing target counts, Purashcharana state, Sandhya observance, pending outbox rows, and sync deadlines.
- [ ] Give existing observed Sandhya records an unknown quantity, not 108/32/64 and not a fabricated zero.
- [ ] Preserve old audio acceptance/count semantics as historical facts. Do not assume every correct practice session contributed to Purashcharana.
- [ ] Retain existing totals as an explicitly identified legacy baseline where exact contributing history cannot be reconstructed. Avoid summing a baseline and the same old sessions again.
- [ ] Do not infer historical Sandhya periods from audio-session clock time alone.
- [ ] Backfill eligible legacy targets to Gayatri only where the existing single-mantra history supports that; mark uncertainty if later catalog changes make the association unclear.
- [ ] Export the schema and verify both a fresh install and supported upgrade paths against it.

### 3.2 Replace incompatible snapshot merging for new counts

`CloudSyncPolicy.purashcharana` and `PurashcharanaReconciler` currently merge counts using `max(local, remote)`. That policy cannot preserve an intentional decrease and cannot combine independent additions to the same baseline from two devices. Do not reuse it unchanged for the new ledger.

- [ ] Extend the existing account-scoped outbox/transport with versioned japa operations and Sandhya observance synchronization.
- [ ] Store entries under stable owner-scoped IDs, for example a proposed private `users/{uid}/japaEntries/{entryId}` collection. Add owner-only rules for the new paths; nested collections are not authorized merely by rules on the parent user document.
- [ ] Apply each operation once using its identity and entry revision. Keep existing stream/checkpoint retry protection, but also deduplicate the underlying entry when a database backup is restored into a new installation.
- [ ] Distinct entries from two devices are additive. Editing one shared entry is a revision replacement, not a second contribution.
- [ ] Proposed conflict policy: accept an update against the expected revision; if two devices edit the same base revision, preserve the rejected local version and offer a clear choice/rebase. Do not silently choose the larger count or add both edits.
- [ ] Retain tombstones long enough that a delayed device cannot resurrect removed entries. Define compaction and device catch-up before pruning them.
- [ ] Synchronize goal identity/enrollment/withdrawal sufficiently to restore allocations. Two vows for the same mantra and target size must remain distinct.
- [ ] Restore paginated history and maintain a cursor/checkpoint. Do not put an unbounded lifetime ledger in a single Firestore document.
- [ ] Use the same projection rules for locally pending entries and cloud-confirmed entries, avoiding an extra increment when upload is acknowledged.
- [ ] Keep the existing deferred network scheduling; never send one network write per counter tap or block count saves on sync.
- [ ] Add visible pending/error/last-success state and explicit retry. Avoid treating successful Google sign-in as evidence that all data is backed up.

### 3.3 Legacy and mixed-version rollout

- [ ] Version the sync contract and decide the minimum compatible client before deployment. Old clients must not overwrite corrected new totals through the old snapshot path.
- [ ] Establish a server-recognized legacy baseline once per account/goal, then apply new ledger operations after the cutover. Do not create a full baseline independently on every device.
- [ ] Account for pending legacy outbox rows during cutover: drain/acknowledge or translate them exactly once before marking a baseline final. Never drop them merely because the schema upgraded.
- [ ] If two devices have divergent legacy aggregate-only histories, do not claim that summing or taking the maximum recovers their true union. Preserve snapshots and offer a reconciliation/import path where the old format lacks enough identity to deduplicate.
- [ ] Gate incompatible legacy writes through backend validation/versioned paths as appropriate, with an understandable update-required state for old clients.
- [ ] Deploy compatible rules and any required aggregation/transaction support before enabling client writes. Use a staging project/emulator to validate the contract and rollback behavior.
- [ ] Update [architecture.md](../architecture.md), [deferred-cloud-sync.md](../deferred-cloud-sync.md), the backend README, and account-deletion handling to match the final data contract.

## 4. Mental-japa UI and teaching — P0/P1

- [ ] Introduce a proposed mental-japa screen/ViewModel, or a clearly separated branch of the existing screen with no dependency on speech initialization.
- [ ] Extend navigation with validated mode, mantra, optional occurrence, and profile context. Apply the first-use teaching panel consistently across entry points, plus a permanent Home guide and a compact reminder/link in both Japa and Sandhya.
- [ ] Scope `VoiceModelGate` and microphone permission handling to deliberate audio learning/counting, rather than the whole Japa destination.
- [ ] Keep counter persistence and UI updates fast; restore drafts through Room rather than relying only on Compose state or a ViewModel's lifetime.
- [ ] Update existing Home/Sandhya/Purashcharana call-to-action labels and count explanations. Review `purashcharana_sankalpa_dialog_body`, `purashcharana_counting_body`, and `purashcharana_complete_body` specifically.
- [ ] Put Sanskrit, transliteration, and mentor-approved explanation in translatable resources/content; store shown/opened and explicitly acknowledged states separately, keyed by profile and guidance version.
- [ ] Include both new and returning users in the guidance change. Re-prompt gently for an unacknowledged/materially changed version, without blocking japa. Keep the guide visible after acknowledgement and independent of rotating/remote Home content.
- [ ] An explicit acknowledgement records the user's action, not their understanding. Keep it local by default; optional account sync must not imply a requirement for developer-level individual tracking.
- [ ] Add debug review fixtures for empty count, 600-count Sayam, below-guidance count, correction, offline save, recovered draft, and long-script guidance.

## 5. Reminder repair — P0

### 5.1 Investigation before changing the mechanism

Capture the affected phone's Android version, manufacturer, installed app build, selected location/timezone, reminder toggle, notification permission, channel state, next scheduled times, and whether “closed” meant backgrounded, dismissed, or force-stopped. These can be collected during device testing; no assumption about the root cause is made in this plan.

Inspect these concrete possibilities:

- [ ] Missing/blocked notification permission or a disabled channel despite enabled scheduling.
- [ ] No scheduled alarm, delayed delivery, an exception during scheduling, or failure to rearm.
- [ ] Startup scheduling versus receiver scheduling, including any replacement of PendingIntents while an alarm is being delivered.
- [ ] Current scheduling skips today when its preparation time has passed, even if the solar event is still ahead. Compute each stage independently so today's event-time alarm remains eligible; test the optional immediate preparation cue.
- [ ] `userId` is read from current preferences in notification actions; an old notification can otherwise act on the wrong profile after switching.
- [ ] `logNotified` is written before `showReminder`, which can return without posting. Existing `NOTIFIED` therefore does not prove the user received a notification.
- [ ] `MY_PACKAGE_REPLACED` recovery is not currently among the listed boot/time-change actions; verify behavior after updates.

### 5.2 Implementation tasks

- [ ] Add proposed reminder stages `PREPARATION` (event minus 15 minutes) and `EVENT_TIME` (event instant), with separate enabled preferences. Use the same model in the scheduler and `SandhyaUiState`; remove the duplicate `GetSandhyaAdherenceLead = 5L` constant.
- [ ] Expand PendingIntent identity from period only to owner/profile + period + stage, with date/generation context validated on receive. Distinct request codes or intent data must prevent the event-time alarm overwriting preparation. Each stage rearms its own next occurrence.
- [ ] Persist stage outcomes separately from the single observance row. Keep the unique owner/date/period Sandhya record; six potential notifications must not become six observances.
- [ ] Handle one/both stages disabled, late enablement, cancelled/replaced alarms, observed-before-event suppression, and a delayed preparation alarm coinciding with the event-time alarm. Dismissing preparation only dismisses that notification.
- [ ] On upgrade, cancel obsolete single-stage PendingIntents, preserve the master enabled choice, expose the newly added event-time switch, and schedule the correct stages exactly once.
- [ ] Update `sandhya_intro` and all timing copy/fixtures. Display requested reminder time separately from actual delivery time in diagnostics.
- [ ] Keep short, bounded receiver work and rearming independent of expensive/cloud work; handle failed scheduling explicitly enough to diagnose it.
- [ ] Persist schedule generation/occurrence identity so late intents from a changed location, profile, or setting can be rejected or handled without duplicate notifications.
- [ ] Add exact date/period/profile context to content and action PendingIntents. Validate context in both cold-start and already-running activity paths.
- [ ] Add the “Set up reminders” panel directly to `SandhyaScreen` with notification permission, channel-settings navigation, optional precision access, battery troubleshooting, a test notification, and both next reminder times. Recheck on resume and after grants/revocations.
- [ ] If using exact alarms, implement capability checks, grant-state handling, rearming, and an inexact fallback. If not using them, hide unsupported access controls. Link to available battery/app settings instead of assuming every manufacturer exposes the same page; no unrelated permissions are required.
- [ ] Keep observance counting independent from reminder delivery. If adherence denominators are revised, use planned occurrences rather than the fact that a notification happened to be posted; do not rewrite old history silently.
- [ ] Cover missing solar events, selected-location timezone versus device timezone, and travel. Preserve the existing solar calculator unless investigation identifies an error.

Inexact alarms and access requirements for exact alarms are documented by Android. Retain an evidence-based precision decision and do not promise that a 15-minute scheduling offset alone fixes delivery latency. [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms/schedule).

## 6. Google sign-in and profile photos — P1

- [ ] Add compatible Credential Manager/Google ID dependencies and exchange a selected Google ID token for a Firebase credential. Keep Android SDK types out of domain contracts.
- [ ] Configure the provider, required signing fingerprints for the distributed builds, web client ID, and refreshed Firebase configuration in the appropriate environment. Verify the actual configuration; source inspection did not inspect the live Firebase console. [Firebase setup](https://firebase.google.com/docs/auth/android/google-signin).
- [ ] Add auth events/results for cancellation, credential selection, sign-in, linking, retry, and session recovery.
- [ ] Implement linking while authenticated to the existing account; recover account collisions through authentication, not email-string matching alone. Handle credentials already linked elsewhere without moving data silently. [Firebase provider linking](https://firebase.google.com/docs/auth/android/account-linking).
- [ ] Extend `AuthAccount`, local profile/entity mapping, and the cloud profile mirror with optional photo URL and source/preference metadata.
- [ ] Preserve fallback avatar behavior and avoid blocking login on photo download. Handle stale/failed image URLs.
- [ ] Review `SyncSignedInUserUseCase`: its current mapping creates a profile by Firebase UID and maps cloud adults to `PARENT`. Define adult/individual presentation deliberately rather than assuming Google sign-in fixes role modeling.
- [ ] Refresh/recover auth state independently of background upload, and clear relevant credential session state on sign-out.
- [ ] Verify local-profile import, linked family records, widget invalidation, and deletion behavior against the new owner mapping.

Do not blindly copy the documentation's newest dependency versions into the existing Kotlin/Compose/Firebase stack. Resolve compatible versions when implementing, then run the relevant build and auth tests.

## 7. Widget — P1

- [ ] Add proposed `presentation/widget/` components, provider metadata, configuration activity/route, and manifest declarations.
- [ ] Evaluate Jetpack Glance against the current dependency stack. Share domain state/presentation formatting; implement the widget UI with supported widget components. [Glance overview](https://developer.android.com/develop/ui/compose/glance).
- [ ] Persist widget configuration per widget ID: stable account/profile, mantra, goal choice, and privacy/display settings.
- [ ] Build widget state from the same local count projections used by the app. Render an intentional empty state if the selected goal no longer exists.
- [ ] Refresh after committed counts/corrections and goal/account changes, with appropriate coalescing. Use a date-change/fallback update for day-dependent labels; avoid per-second or exact-time widget refresh promises.
- [ ] Start/resume the mental-japa route through a validated PendingIntent/deep link. Support cold launch and warm `onNewIntent` handling.
- [ ] Invalidate stale owner data on sign-out/account switch and prevent repeated taps from creating duplicate drafts.
- [ ] Add launcher preview, resize variants, TalkBack labels, and empty/offline/completed examples to visual QA.

## 8. Verification matrix

These are future implementation checks, not results of this documentation task. Prefer tests of real invariants and user outcomes over tests that merely mirror code structure.

| Area | Required cases | Level |
| --- | --- | --- |
| Arithmetic | 108 + 32 + 600 = 740; default minima total 204 without auto-credit; add-more vs edit-total; invalid/overflow inputs | Domain |
| Goals | Same event in two eligible views counted once in lifetime total; wrong mantra excluded; no-goal save; stable allocation after target replacement; cap/overshoot; bulk milestone crossings | Domain/data |
| Corrections | 600 to 500; delete/undo; completion becoming incomplete; cap still reached after a decrease; withdrawn vow stays withdrawn | Domain/data |
| Legacy counts | Preserve v9 targets, Purashcharana, audio sessions, unknown Sandhya quantities, and pending outbox work; avoid baseline/session duplication | Migration/instrumentation |
| Atomicity | Failure between transaction steps rolls back; after commit count and outbox both survive reopen; duplicate operation UUID is harmless | Room instrumentation |
| Drafts | Rotation, background, process death, resume/discard, partial save, repeated finish, midnight, profile switch | ViewModel/instrumentation |
| Sync | Offline queue, reconnect, timeout, commit-with-lost-ack, duplicate restore, account isolation, pagination, deletions, old-client writes | Data + Firebase emulator/staging |
| Two devices | Independent additions combine; same-entry concurrent edits retain conflict data; stale snapshot cannot restore a corrected count | Integration |
| Silent japa | Works with denied microphone and missing voice model; no audio capture/playback on entry; reference playback creates no credit | UI/instrumentation |
| Audio regression | Learning evaluation and scoring still work; retained accepted NORMAL session creates one contribution; PRACTICE creates no goal credit | Existing/new domain and ViewModel tests |
| Reminders | -15 and 0-minute calculations; six distinct stage alarms with three observance rows; late enable preserving event-time alarm; one/both stages disabled; observed suppression; delayed-stage coalescing; reboot/update/timezone change; next-day rearming | Domain/receiver + real device |
| Sandhya setup | Permission denied/granted/revoked; channel blocked; return from settings; optional precise-access fallback; battery-settings unavailable; user-confirmed test notification; no developer-message opt-in side effect | UI + real device |
| Delivery defect | Background, removed from recents, process absent, screen off, idle/battery saver, offline, multiple days without opening; explicit force-stop documented separately | Mentor's phone or equivalent |
| Google auth | New account, existing linked account, collision recovery, cancel, offline, token expiry, missing photo, role mapping, sign-out/switch | ViewModel + integration/device |
| Widget | Correct target/mantra, cold/warm launch, draft resume, refresh after edit, no target, completed/removed target, offline, stale owner, resize | Instrumentation + launcher review |
| Teaching/accessibility | Guidance at first entry and permanent Home/Japa/Sandhya placement; dismiss then revisit; explicit acknowledgement per version; offline availability; remote cards cannot hide it; all six cautions; large text and TalkBack | Content review + UI |
| Remote publishing | Authorized/unauthorized publish, draft isolation, schedule/expiry, revision/unpublish, cache/fallback, no APK update, unchanged permanent guide | Backend rules + UI/integration |
| Developer push | Test audience, opt-out/quiet hours, token rotation/multiple devices, foreground/background/cold tap, duplicate send/receive, expiry/cancellation, no accidental send on content edit | Backend + real devices |

Use the existing module checks when implementation lands:

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

The current Android build declares minSdk 33 and target/compile SDK 36. Include supported OS versions and the mentor's manufacturer in device coverage. Record actual reminder delivery offsets and receiver/rearm evidence, not just notification screenshots captured after opening the app.

## 9. Suggested work packages and completion checklist

| Package | Scope | Depends on |
| --- | --- | --- |
| W1 | Guidance/eligibility decisions, copy drafts, schema and sync contract | Mentor/product review items in the overview |
| W2 | Japa ledger, draft persistence, local transactions, allocation/correction logic, migration | W1 |
| W3 | Compatible outbox operations, server/rules changes, baseline cutover and history restore | W1; integrates with W2 |
| W4 | Sandhya entry, mental japa, permanent guidance/acknowledgement, Home/Progress/Purashcharana changes | W2; production rollout needs W3 compatibility |
| W5 | Reminder reproduction, in-Sandhya setup, preparation and event-time alarms, diagnostics, lifecycle repair | Independent investigation; W4 routes for count actions |
| W6 | Google sign-in, account linking, optional photo, local-profile import | Stable identity contract from W1/W3 |
| W7 | Widget, shared progress state, navigation and launcher QA | W4 navigation and W6 account-state handling where applicable |
| W8 | Release C1: remote content schema, cache, Home renderer and protected publishing | Stable Home/navigation contracts; see dedicated plan |
| W9 | Release C2: announcement channels/preferences, installation registrations, admin test/send/schedule, safe content navigation | W8 content identities and trusted backend |
| W10 | Release C3: publishing/sending rollout, expiry/unpublish/rollback, device validation and delivery reporting | W8/W9 |
| W11 | Release D: optional personal targets/checklist/pacing/export/reflections | Requested P0/P1 behavior stable |

- [ ] Every MF requirement in the overview is mapped to implemented behavior or explicitly scheduled remaining work.
- [ ] Code comments, strings, enrollment explanation, and tests no longer contradict accepted manual/mental counting.
- [ ] Legacy users retain counts through upgrade and mixed-version rollout without fabricated historical data.
- [ ] The mentor reviews the three methods, six cautions, minima, and count-completion wording.
- [ ] The practice guide stays visible in Home/Japa/Sandhya after onboarding and after remote content changes; explicit acknowledgement never gets inferred from display alone.
- [ ] The complete 740-count workflow passes offline, after correction, and after cross-device restoration.
- [ ] Sandhya setup and both reminder stages have real-device evidence and a documented resolution or precisely bounded remaining issue.
- [ ] Google sign-in preserves account identity; the widget opens the correct mantra and reflects the same totals.
- [ ] An authorized developer can change supported Home content and send a test teaching notification to a compatible installed app without updating its APK; ordinary users cannot publish or send campaigns.
- [ ] Update architecture, verification notes, demo script, relevant screenshots, and backup/privacy descriptions for the implemented release.
