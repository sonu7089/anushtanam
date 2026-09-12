# Database correctness and query audit

Reviewed 12 September 2026 against source commit `5c5aaba`. This extends the [UI audit](README.md) and [lazy-loading plan](lazy-loading.md). See the separate [polling audit](polling-audit.md) for CPU wakeups and scheduling changes.

**The local ledger has good transactional foundations, but the boundaries around it need work.** The highest-priority findings are a first-install profile-seeding race, non-atomic cloud-summary/child-goal accounting, permissive cloud validation, and a missing trigger for receiving remote-only ledger changes. Query optimization should follow or accompany those correctness fixes.

The original audit did not change application code, production databases, cloud deployments or rules. Its scripts use synthetic data and a local Firestore emulator only. Follow-up: DB-01 and the step 02 implementation for DB-03/04/12 are now locally verified as recorded below. Step 02's stricter rules were deployed to `anushtanam-shastralife` on 12 September 2026 at the user's request. Other findings remain open. The initial environment limits in this audit were resolved during [step 00](baseline.md).

## Scope and evidence

Reviewed all 14 exported Room v13 tables, DAO queries, repository write paths, migration definitions, japa allocation/projection, manual count editing, session persistence, cloud outbox/transport/recovery, Firestore rules and periodic consumers. This is a source and targeted reproduction review, not a certification that every possible interleaving is correct.

| Verification | Result and limit |
| --- | --- |
| Exported v13 DDL loaded into Python SQLite 3.49.1 | Schema/constraint reproductions and nine query-plan comparisons completed; synthetic in-memory data, not Android timing |
| New local Firestore rule probes | Four passed: three reproduce current gaps; one confirms another account cannot write the owner's ledger |
| Existing backup/family rules tests | 25 passed in the local emulator; these tests do not cover the reproduced gaps |
| Android data tests attempted | Blocked: configured SDK lacks `android-36` |
| Domain tests attempted separately, offline | Blocked: required Kotlin/coroutines/serialization dependencies unavailable in the offline cache |
| Real-device Room, migration and two-device sync tests | Not run; no device measurements or live-data conclusions |

Reproduction sources: [inspect_database.py](inspect_database.py), [database-evidence.json](database-evidence.json), [database-rules-audit.cjs](database-rules-audit.cjs). Console logs from this review are under `build/perf-audit/`. The Python migration check executes the problematic identity assignment on compatible target rows, not the complete Android migration chain.

## Data ownership and counting invariants

| Data | Current responsibility |
| --- | --- |
| `user_profiles` | Local profile ID and unique nullable Firebase UID association |
| `recitation_sessions` | Audio assessments, both PRACTICE and NORMAL; full performance history |
| `japa_entries` | Uncapped performed quantities, source, revision, original target/vow allocation and optional audio-session link |
| `targets`, `purashcharana` | Goal definitions, legacy baselines and capped projections from the ledger |
| `sandhya_counts`, `sandhya_count_changes`, `sandhya_count_drafts` | Current manual total per period, saved operations and one persisted draft per user |
| `sandhya_logs` | Reminder/observance history, independent of manual japa quantities |
| `cloud_sync_outbox`, `cloud_sync_schedule` | Durable pending operations and retry schedule |
| `backup_queue_state`, `backup_state`, `backup_conflicts` | Payload fingerprints, download checkpoints and competing revisions |
| Firestore owner collections | Versioned japa, goals, performance records, conflicts and a restore change feed |
| Firestore `progress` / `goals` | Separate compact analytics and parent-assigned cloud-goal projections |

Preserve these invariants during optimization:

- A correct NORMAL audio session contributes one performed japa; PRACTICE and failed sessions do not. One performed entry can credit a target and a vow without multiplying lifetime performed count.
- Goal display caps must not truncate the underlying performed quantity; correcting an overshoot must recompute from the uncapped entries.
- Replaying a saved manual operation ID must not add again; a stale editor revision must not overwrite a newer total.
- Correction keeps original allocation. Entries with no applicable goal remain valid performed practice.
- A local save and the durable events needed to reconstruct every promised projection must commit together.
- User ownership, mantra identity, original date/timezone and stable record identity must survive migration, account changes and restore.

Current strengths: `RecitationRepositoryImpl.saveSession` commits session, eligible ledger entry, goal projection and full-session backup event in one Room transaction. `SandhyaCountDao.save` checks operation identity and base revision and saves its count/change/projection transactionally; its repository adds backup enqueueing in the surrounding transaction. `JapaAccounting.credited` uses `BigInteger` before capping. Restore uses transactional page checkpoints and deterministic revision tie-breaking. The migration builder has no destructive fallback. These should remain intact.

## Prioritized findings

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| DB-01 | P1, resolved | Async demo seed could overwrite a newly created real profile | Seed removed; production-factory Room regressions pass on device (see resolution below) |
| DB-02 | P1 | Cloud summary and parent-goal deltas can be lost after a successful local save | Confirmed transaction/lifecycle gap; crash injection pending |
| DB-03 | P1, fixed | Child goal rules allowed unexpected fields and invalid counts | Rejection regressions pass; rules deployed |
| DB-04 | P1, fixed | Invalid owner records could poison restore; canonical writes could omit the feed | Validation/quarantine and atomic feed tests pass; rules deployed |
| DB-05 | P1 | A settled device has no trigger to receive another device's ledger changes | Confirmed trigger gap; two-device timing test pending |
| DB-06 | P1, resolved locally | Legacy target identities collide if a profile has multiple unfinished targets | Room v15 repair before uniqueness; all supported migration starts verified (see step 03) |
| DB-07 | P2 | Initial cloud daily analytics are approximated and rebuilt on every attempt | Confirmed calculation |
| DB-08 | P2 | Projection and backup repeatedly scan/serialize full history | Confirmed query/call structure |
| DB-09 | P2 | Home/Progress day-dependent values do not necessarily refresh at midnight | Confirmed missing time input; device boundary test pending |
| DB-10 | P2 | Missing lookup/order indexes; long lists remain unbounded | SQLite plans and instruction counts |
| DB-11 | P2, resolved locally | Database constraints do not enforce several ledger identities/relationships | Unique identities/associations, immutable assessments and eventual-reference admission verified (see step 03) |
| DB-12 | P2 boundary, fixed locally | Valid per-entry Long values can overflow a merged manual-period total | Exact Long aggregate admission bound; excess retained for repair |

P1 means address before claiming reliable multi-device counting. “Conditional” and “boundary” identify prerequisites, not evidence of existing user data loss.

### DB-01 — Release demo seeding races with the first real profile

At the audited commit, [MantraSiddhiDatabase.kt](../../core/data/src/main/java/com/mantrasiddhi/data/local/MantraSiddhiDatabase.kt), lines 349–365, launched an unawaited IO coroutine in `onCreate`, inserting fixed IDs 1–4. `UserDao.insertAll` used `OnConflictStrategy.REPLACE`. Both the callback and that unused bulk API have now been removed.

An allowed ordering is: database opens, first sign-in creates the real profile with auto-ID 1, then the delayed seed replaces ID 1 with Arjun and a null Firebase UID. Session/preference/history references may still point to ID 1. The in-memory SQLite reproduction confirms the replacement behavior; it does not measure how frequently Android schedules this interleaving.

**Fix:** keep demo fixtures in the debug review environment. If any production seed is required, insert it synchronously within initial database creation using non-destructive identities/semantics, before application access. Add an initialization race test with real profile insertion ahead of the seed. Never use REPLACE to initialize user-owned records.

#### DB-01 resolution — step 01, 12 September 2026

Production database creation now only creates the Room schema; it schedules no profile writes. Account/onboarding flows remain responsible for profile creation. Removed the DAO-provider dependency from [DatabaseModule](../../core/data/src/main/java/com/mantrasiddhi/di/DatabaseModule.kt) and the unused `UserDao.insertAll` REPLACE operation. Existing debug review fixtures remain isolated from the database. The factory accepts an optional database name so tests exercise the same production configuration on disposable files.

No schema version change, cleanup or data migration is needed. Existing rows, including IDs 1–4 and profiles with old demo names, are preserved: names and IDs alone cannot establish that a row is disposable. This prevents future seed overwrites; it does not reconstruct identity information already lost to an older app's seed.

[DatabaseInitializationTest](../../core/data/src/androidTest/java/com/mantrasiddhi/data/local/DatabaseInitializationTest.kt) covers:

- An empty first-install database, an automatically assigned real profile ID of 1, and unchanged account/photo fields after reopening.
- A coroutine gate holding startup database access until a separate caller has committed the real ID-1 account and history. Releasing that gate and completing the delayed open leaves the profile, sessions, targets, vows, Sandhya logs and japa entries unchanged. This deterministically tests delayed access without timing sleeps; the old asynchronous seed path is removed entirely.
- Reopening an existing v13 file containing all four legacy IDs/names, parent/child relationships, a linked Firebase UID and history for each profile. Full entity comparisons verify preservation, including session and goal associations.

Verification: **181 unit tests** (102 data, 79 app), zero failures/errors/skips; app debug compilation/Hilt generation and data instrumentation APK assembly pass. **14 Room device tests** pass on the connected Pixel 7 Pro: three `DatabaseInitializationTest`, ten `MantraSiddhiDatabaseTest`, and one `GoogleProfileMigrationTest`. All initialization files are isolated in the data test package and deleted afterward; the installed application database is untouched. Commands and retained logs:

```powershell
.\gradlew.bat :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:assembleDebugAndroidTest --console=plain
adb -s SERIAL install --no-streaming -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -e class com.mantrasiddhi.data.local.DatabaseInitializationTest,com.mantrasiddhi.data.local.MantraSiddhiDatabaseTest,com.mantrasiddhi.data.local.GoogleProfileMigrationTest com.mantrasiddhi.data.test/androidx.test.runner.AndroidJUnitRunner
```

Build/unit log: `build/perf-01-verification.log`; device log: `build/perf-01-device-correctness.log` (`OK (14 tests)`). The build required access to the installed SDK/cache outside the sandbox, as in step 00. No full migration-chain, cloud-recovery or performance improvement claim is made by these focused correctness checks.

### DB-02 — Ledger durability does not cover all displayed counts

[EvaluateRecitationUseCase.kt](../../core/domain/src/main/kotlin/com/mantrasiddhi/domain/usecase/EvaluateRecitationUseCase.kt), lines 94–103, saves the session first and only then calls `progressSyncRepository.recordSession` in a best-effort block. [RecitationViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/recitation/RecitationViewModel.kt), around lines 562–579, launches another ViewModel job to enqueue a parent-goal increment after evaluation. That operation has no session ID in its payload; [FirestoreGoalRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirestoreGoalRepositoryImpl.kt) enqueues an empty `CHILD_GOAL` payload.

Process death, cancellation or enqueue failure after the local transaction can leave correct local japa/full-session backup but no compact-progress or parent-goal delta. The recovery scanner reconstructs practice records and ledger entries, not these missing deltas. When a cloud progress document already exists, its values can remain below restored local history; Progress prefers that cloud summary.

**Fix:** enqueue all required accounting events inside the original local transaction, with session stable ID, original account and occurrence time. Make downstream projection idempotent by event identity. Preserve the rule that manual japa does not earn parent-assigned audio rewards. Reconcile existing compact summaries from canonical sessions rather than replaying anonymous increments. Do not add a second independent increment beside the existing path.

**Test:** terminate immediately after local commit; restart/recover and verify ledger, full history, cloud analytics and assigned-goal progress agree exactly once. Test retries, account switch, midnight and competing attempts. The older `IncrementTargetUseCase` and `incrementJapa` repository API still mutate projections directly, but current evaluation does not invoke them; deprecate or constrain those legacy APIs instead of alleging current double increments from their unused constructor parameters.

### DB-03 — Cloud goal validation contradicts its stated field restriction

[firestore.rules](../../backend/firebase/firestore.rules), lines 202–207, checks that a child preserves participant IDs, reward fields and target size, but does not restrict all changed keys or validate the progress values. The emulator accepted a child update setting count to `-5`, completion to `true`, `createdAt` to null and an extra field. This affects a parent-visible record; it is more than a malformed private cache.

**Fix:** explicitly allow only intended changed keys, validate integer bounds and completion/count consistency, and protect creation time and schema. Define whether this product trusts self-reported progress: field validation alone cannot prove a mantra was actually recited. If reward integrity requires stronger evidence, perform the projection in a trusted backend from validated immutable events. Firebase documents [`diff().affectedKeys().hasOnly(...)` field restrictions](https://firebase.google.com/docs/firestore/security/rules-fields).

### DB-04 — Weak payload validation can stop restore repeatedly

Owner `japaEntries` creation rules validate quantity/revision and a few strings but omit date format, timezone, source/period relationships, timestamps and many required fields. Goal rules similarly omit several fields the merger requires. The emulator accepted `dateKey: "not-a-date"` and `zoneId: "not/a/zone"` together with a canonical matching feed record.

[JapaReplicaMerger](../../core/data/src/main/java/com/mantrasiddhi/data/sync/JapaReplicaMerger.kt), [SandhyaCountDao.rebuildPeriods](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/SandhyaCountDao.kt), and downstream date parsing assume valid payloads. A malformed period entry can be committed in a download page and then fail final projection or a UI summary; later attempts encounter it again. This is an owner-data validation/compatibility gap, not evidence that strangers can modify private history. The cross-account denial control passed.

The rules validate feed → canonical consistency, but not canonical → required feed publication. A separate emulator probe successfully created a canonical entry with no feed document. The current client normally writes both transactionally; another client version can legally omit the feed, making changes invisible to incremental restore after bootstrap.

**Fix:** version and validate payloads before committing pages; enforce required fields, types, safe bounds and basic date structure in rules, with full date/timezone validation in the client/backend. Quarantine invalid records with a surfaced repair state rather than poisoning unrelated history or silently dropping them. Require coupled feed publication on canonical changes, or generate the feed in a trusted backend. Firebase's [atomic write validation](https://firebase.google.com/docs/firestore/manage-data/transactions) describes the `getAfter` mechanism.

#### DB-03/04 resolution — step 02, 12 September 2026

The [step 02 implementation and evidence](step-02-validation.md) records strict assigned-goal and backup schemas, required canonical/feed coupling, per-record validation before Room admission, durable account-scoped quarantine, transactional checkpoints, bounded rotating repair retries and Settings' incomplete-count state. Room v14 also performs a one-time canonical compatibility scan. Verified 188 unit tests, 56 backend tests, 28 Room/Firebase device tests and four backup UI tests; app lint has no errors. The original successful-invalid-write probes are now rejection regressions.

The stricter rules were deployed to `anushtanam-shastralife` on 12 September 2026 following the user's explicit instruction; the updated debug APK was reinstalled on the connected device. See [deployment evidence](step-02-validation.md). The [compatibility plan](step-02-validation.md#compatibility-and-deployment-plan) covers installed writers, malformed/immutable historical documents, missing-feed backfill and previously corrupted local data. Runtime admission prevents new contamination; it does not rewrite already corrupted local history or certify all installed clients and production records.

### DB-05 — Remote-only changes are not discovered by a settled device

[CloudSyncCoordinator](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncCoordinator.kt) starts its foreground loop only when local uploads or `restorePending` exist. [JapaCloudSync.requestRecovery](../../core/data/src/main/java/com/mantrasiddhi/data/sync/JapaCloudSync.kt), lines 66–72, creates state only if absent and does not mark an already-settled account for another pull. `needsRestore` remains false after a successful restore. Foreground `capture` detects local changes only. There is no backup-feed listener or backup-specific push invalidation in the reviewed paths.

Scenario: devices A and B are fully settled; A adds manual japa and syncs; B makes no local changes. B has no trigger to pull A's new entries, even when reopening causes another unchanged capture. [BackupViewModel.syncNow](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/BackupViewModel.kt) also refuses when `waiting` is false, so the apparent manual control is not an unconditional remote refresh. Compact Firestore summary listeners are separate and do not restore the ledger.

**Fix:** explicitly support a remote refresh from an idle state. On relevant foreground entry, perform one bounded incremental pull after a freshness interval, or observe a lightweight per-account change/version signal while active. Coalesce notifications into one pull. Avoid solving this with rapid full-history polling. Verify A-only changes arrive on B without requiring B to create dummy practice.

### DB-06 — Migration can collapse distinct legacy target IDs

[MIGRATION_11_12](../../core/data/src/main/java/com/mantrasiddhi/data/local/MantraSiddhiDatabase.kt), line 290, sets every unfinished target's stable ID to `legacy-target`. The older target table permits multiple unfinished rows for the same profile, and the migration neither reconciles them nor adds a uniqueness constraint. With two such rows, the exact assignment produces duplicate `(userId, stableId)` values.

`JapaDao.project` credits by stable ID, so both rows can receive the same future entry. Cloud enqueueing uses that identity as one goal document/fingerprint, collapsing distinct definitions and baselines. Vow IDs derived only from start milliseconds also lack a uniqueness guarantee, though no collision frequency was measured.

**Fix:** define a deterministic compatibility identity per logical legacy goal, explicitly choose any single mirror-compatible active goal, and preserve distinct historical rows. Repair existing duplicates and their allocations before adding uniqueness. Test databases with multiple unfinished legacy targets, completed targets and several profiles; do not regenerate all IDs indiscriminately and break existing cloud associations.

**DB-06 resolution:** [Step 03's identity repair](step-03-identities.md) preserves the evidenced canonical cloud identity, repairs colliding rows and allocations, archives originals, then enforces uniqueness. Device verification covers all supported migration starting versions and deterministic compatibility IDs across different local profile/row numbers. Previously overwritten cloud distinctions cannot be reconstructed automatically; see the documented repair limits.

### DB-07 — Daily analytics seed is knowingly approximate

[FirestoreProgressRepositoryImpl.buildSeedData](../../core/data/src/main/java/com/mantrasiddhi/data/remote/FirestoreProgressRepositoryImpl.kt), lines 98–124, uses correct-session count for daily total attempts and multiplies that count by the lifetime average score for daily score sum. A day containing one score-1.0 correct attempt and another day containing one score-0.0 failed attempt yields lifetime average 0.5; the first day's seeded average becomes 0.5 and the failed-only day disappears. Future increments do not repair the missing historical daily values.

The seed is also built on **every** `recordSession` call, even when the cloud document already exists and only a delta is needed: aggregate scans, map creation and a repeated daily seed are added to each queued event. The component queries are not a single consistent snapshot.

**Fix:** use exact per-day COUNT, correct-count and SUM(score) aggregates in a transactional snapshot; use the persisted session's date rather than a later enqueue-time `LocalDate.now`. Seed once at a defined event boundary and make subsequent records compact identity-based deltas. Test concurrent saves around first bootstrap, failed-only days and existing remote summaries. Keep accounting meanings explicit: audio assessment accuracy differs from performed manual japa.

### DB-08 — One count change causes broad reads and writes

[JapaDao.project](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/JapaDao.kt), lines 91–115, loads all entries and scans them once for each target and vow, then updates every projection even when unchanged. Approximate CPU work is `O(entries × goals)`. `creditUnassignedTargetEntries` adds further reads. [TargetRepositoryImpl.getAllForUser](../../core/data/src/main/java/com/mantrasiddhi/data/repository/TargetRepositoryImpl.kt) runs a credit-repair transaction at subscription start, so merely observing a page is not always read-only.

[JapaCloudSync.enqueue/capture](../../core/data/src/main/java/com/mantrasiddhi/data/sync/JapaCloudSync.kt) scans ledger/goals; [PracticeBackupStore.capture](../../core/data/src/main/java/com/mantrasiddhi/data/sync/PracticeBackupStore.kt) restarts all history pages from the beginning, serializes each row and compares fingerprints. Each session adds an `audioEntry` query. The current index only narrows that query by user, making a full capture capable of quadratic session/entry lookup work. Paging at 200 bounds peak list size, not the total work of each capture.

The coordinator's table invalidation observer triggers another broad capture after 750 ms. Unchanged projection writes and replayed restore updates can create additional invalidations. Fingerprint de-duplication prevents many duplicate uploads but does not avoid the scans, queries, serialization or invalidation processing.

**Fix:** keep changed-record IDs and enqueue them transactionally; reserve full capture for bootstrap, explicit repair or a versioned migration. Group entry quantities by target/vow once, or query only affected allocations; avoid updating unchanged projection rows. Index audio association lookup. Preserve operation identities, corrections, rollback and replay guarantees. A simple high-water mark alone is insufficient for corrections to older records; track updates as well as inserts.

### DB-09 — Calendar changes need explicit invalidation

[HomeViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt) computes `todayJapa` using `LocalDate.now()` when its combined data emits. Dashboard streaks and [ProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressViewModel.kt) rolling dates likewise depend on data emissions. A quiet screen left open over midnight can retain yesterday's values until some unrelated emission. Resuming Home refreshes the reflection, not necessarily its count state.

**Fix:** combine small date-dependent projections with a calendar-day signal, scheduled at the next relevant midnight and refreshed on resume/timezone/time changes. Keep original recorded entry dates intact and define the timezone policy for legacy audio fallback. Do not rescan history every second to reset a daily label. Verify midnight, device timezone changes and return from background without new practice.

### DB-10 — Query plans show targeted index opportunities

The synthetic fixture uses ten users, 1,000 sessions/ledger entries/changes/outbox rows per user and 100 targets per user. Counts below are **SQLite VM instructions**, not milliseconds, CPU percentages or expected phone speedups. Candidates were installed together in memory; no production indexes were added.

| Query | Current plan/cost | Candidate result | Recommendation |
| --- | --- | --- | --- |
| Active target | Full table scan + temporary sort; 3,449 steps | 45 steps | Consider `(userId,isActive,isCompleted,startedAtMillis,stableId)` |
| Session → audio entry | Scans user entries; 5,025 steps | 31 steps | `(userId,sessionId)`; unique only after duplicate audit/repair |
| Ordered japa history | User filter + temporary sort; 38,015 steps | 19,014 steps | `(userId,createdAtMillis,id)` plus bounded/projection queries |
| All sessions by ID | User filter + temporary sort; 40,016 steps | 22,015 steps | `(userId,id)` if ID ordering remains required |
| Backup session page | Rowid range; 4,618 steps | 4,626 steps | No win in this grouped fixture; validate mixed-user distribution before adding solely for this query |
| Backup change page | Primary ID range; 4,018 steps | 3,824 steps | `(userId,id)` may help mixed-user data; weigh write/storage cost |
| Period change history | Filter + temporary sort; 36,028 steps | 18,039 steps | Extend to `(userId,dateKey,period,revision)` and paginate |
| Pending progress overlay | User-wide filter; 5,367 steps | 522 steps | `(uid,kind,id)` for large mixed outboxes |
| Recent 20 sessions | Existing `(userId,createdAt,id)` index; 476 steps | 474 steps | Already suitable; retain it |

Exact SQL, parameters and full plans are in [database-evidence.json](database-evidence.json). SQLite's [query planner](https://www.sqlite.org/queryplanner.html) and [EXPLAIN QUERY PLAN](https://sqlite.org/eqp.html) explain index searches and temporary sorting. Plans vary by SQLite version/statistics/data distribution.

Use date-bounded/aggregate projections for dashboards and reminders, and lazy paging for long history. The earlier UI-04 finding still applies: five Progress session flows plus a japa-related session flow duplicate history reads. Keep original timezone behavior and exact large totals when moving aggregation into SQL. Profile indexes with write-heavy workloads before adding all candidates; overlapping indexes consume space and slow inserts/updates.

### DB-11 — Missing database-level identity and relationship guarantees

The v13 schema has no foreign keys on any of its 14 tables. `targets` has no indexes; target/vow stable IDs are not unique, session `(userId,stableId)` is a non-unique index, and `(userId,sessionId)` is not unique in `japa_entries`. The script inserted two accepted entries for the same session; the schema allows it. Normal `recordAudio` uses a transaction and checks for an existing entry, so this is not evidence that ordinary sequential saves currently duplicate credit.

Restore, migration, direct DAO calls and future writers still rely on conventions. The public `saveSession` supports replacing an existing assessment, while `recordAudio` returns early for an existing accepted entry or a newly incorrect assessment; changing eligibility can leave the old credit intact. Current evaluation inserts new sessions, so treat this as an API invariant to close, not a demonstrated user edit flow.

**Fix:** make saved assessments explicitly immutable or implement a transactional correction protocol; use stable operation IDs for retriable inserts. Audit duplicates/orphans before adding uniqueness and appropriate ownership references. Restore ordering deliberately allows a ledger entry to arrive before its session/goal, so immediate foreign keys cannot simply be added everywhere; use staging or deferred validation. Prefer preserving history over cascading deletes. Document which references are intentionally eventual.

**DB-11 resolution:** [Step 03's contracts and verification](step-03-identities.md) add immutable assessment saves with stable-ID retries, database uniqueness, validated ownership/known references, persistent eventual associations and per-record quarantine. Verified 548 unit tests, 50 Room/contract device tests and three Firebase recovery device tests. Immediate goal/session foreign keys remain intentionally absent to support either restore order; raw SQL remains a trusted migration/internal boundary.

### DB-12 — Merged manual totals can exceed Long capacity

Local input permits `Long.MAX_VALUE`, and individual cloud quantities are nonnegative Firestore integers. Two offline devices can independently add valid large amounts for one period. `rebuildPeriods` sums using `BigInteger` then calls `longValueExact()` for the `sandhya_counts.quantity` Long. Two maximum values total `18446744073709551614`, outside that representation; final restore fails instead of presenting a reconciled total. This is an extreme input boundary, not a realistic ordinary chanting rate.

**Fix:** choose and enforce a documented domain bound across all entry/merge paths, or use an exact wider persisted representation consistently for period totals. Keep legitimate records recoverable; do not silently clamp performed quantity. Test merged overflow, conflicting revisions and a correction after an oversized remote total.

#### DB-12 resolution — step 02, 12 September 2026

Manual periods retain an exact nonnegative Long representation. Restore computes a candidate aggregate with BigInteger before admitting a winning revision; an overflow-causing record remains in quarantine with its original quantity, while valid history and other periods continue restoring. Settings explicitly reports incomplete counts. Device tests cover two maximum entries, correction freeing capacity for the deferred entry, and a subsequent local edit without clamping. See the [count representation decision and limits](step-02-validation.md#count-representation-decision).

## Repair order and regression requirements

1. Remove the production seed race; tighten goal/backup validation and add negative emulator tests. Preserve current ownership protections.
2. Make summary and assigned-goal events transactional and identity-based; add crash-boundary and retry tests. Repair existing projections from canonical records with an explicit versioned process.
3. Add bounded idle remote refresh and repair legacy identity collisions. Test two offline devices, restore interruptions, edits after completion and account switch mid-download.
4. Introduce changed-record capture, targeted projection updates, measured indexes and paged history. Check insert/update latency as well as reads.
5. Replace avoidable polling using the [polling plan](polling-audit.md), including calendar invalidation and retry backoff.

Extend existing `JapaAccountingDatabaseTest`, `SandhyaCountDatabaseTest`, `BackupRecoveryTest`, `CloudSyncDatabaseTest` and migration tests. Include replay, stale drafts, overshoot/correction, parent-goal eligibility, mismatched mantra, account ownership, legacy duplicates and large totals. Run complete supported-version migration chains with realistic mixed histories once Android SDK/device tests are available. Do not deploy a migration that blindly adds a unique index before repairing rows that violate it.

## Reproduction commands

Run `python doc/perf/inspect_database.py` from the repository root. It creates only in-memory synthetic databases and rewrites the JSON evidence file.

The rules probe requires existing backend test dependencies and a local Firestore emulator. For the cached emulator used in this review, start it in one terminal:

```powershell
java -jar C:/Users/PC/.cache/firebase/emulators/cloud-firestore-emulator-v1.19.8.jar --host 127.0.0.1 --port 28081 --webchannel_port 28082 --project_id demo-perf-audit --rules backend/firebase/firestore.rules
```

In another terminal at the repository root:

```powershell
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:28081'
node --test doc/perf/database-rules-audit.cjs
```

The probe refuses non-loopback endpoints and uses a `demo-` project. Its current `assertSucceeds` cases intentionally prove unacceptable writes are allowed; after fixes, convert them to rejection regression tests. Passing these audit probes means the documented gaps were reproduced, not that the rules are correct.
