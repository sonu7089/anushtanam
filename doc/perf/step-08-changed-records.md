# Step 08 — changed-record backup and targeted database work

Implemented and locally verified on 12 September 2026. Resolves DB-08 and RW-06/RW-07, and the database query/index portion of DB-10. Visible-demand collection and UI paging remain step 09.

## Durable mutations and bootstrap

Room v17 adds `changed_records`, keyed by local profile, kind and record ID. [ChangedRecordJournal](../../core/data/src/main/java/com/mantrasiddhi/data/local/ChangedRecordJournal.kt) installs SQLite triggers through the production database callback, also used by device fixtures. Inserts and relevant updates record identities in the source transaction, including reminder DAO writes that previously depended on full capture. Repeated identities coalesce. Normal practice saves still drain their records into the existing outbox before committing, retaining independent outbox revisions. Goal withdrawal remains an update; this introduces no physical-history deletion or cloud tombstone protocol.

The trigger excludes an existing key before inserting. The first device run exposed that Room's outer `INSERT/UPDATE OR ABORT` can override `OR IGNORE` inside a trigger; the final implementation and regressions cover this interaction.

[PracticeBackupStore](../../core/data/src/main/java/com/mantrasiddhi/data/sync/PracticeBackupStore.kt) consumes at most 200 identities per page. Each page reads current rows, canonicalizes payloads, enqueues uploads and clears identities transactionally. Failure/cancellation retains work; a mutation between pages creates another event. Session payloads use the persisted accepted-entry backlink. Normal saves no longer snapshot the ledger or serialize every goal.

Each account/local-profile pair receives one `capture:v1:<profile>` bootstrap. Its SQL identity seeds and marker commit together, without materializing source history. A process interruption after the marker leaves remaining identities available after reopen. Account linking/re-keying receives a separate bootstrap even after another account drained that profile. Legacy fingerprints normalize once per record and receive a `fingerprint:v1:<key>` marker; current fingerprints compare directly without decoding/re-encoding the saved payload.

The v16→v17 migration seeds legacy allocation, projection and period repair markers after v15's duplicate repair and uniqueness constraints. Full goal repair and versioned bootstrap remain available for compatibility. Unchanged foreground attempts retain step 07's retry/discovery protocol while avoiding local history capture. The coordinator observes durable changed-account identities alongside outbox/restore work, replacing broad table-invalidation capture requests. The runner retains a job for local changes arriving during an attempt.

## Projection and bounded queries

[JapaDao](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/JapaDao.kt) tracks affected target/vow IDs and manual periods. Normal projection reads only those allocations in 200-row pages, and writes goals only when count/completion changes. Capped totals use bounded nonnegative addition without Long overflow. Underlying quantities remain uncapped, preserving downward corrections after overshoot. Manual-period reconciliation uses exact BigInteger accumulation and the existing overflow admission contract.

An incoming unallocated entry gets its own allocation event. Goal creation/change and legacy initialization retain the broader **unclaimed-only** repair pass, in bounded pages. Recorded-timezone eligibility, same-day late-goal credit and original allocations remain intact. Target reads consume pending repair markers; repeated unchanged reads perform no ledger repair scans or projection writes. Restore replay skips equal entry, goal, log, fingerprint and state updates.

Corrections visit only their manual period, newest entries first with a deterministic ID tie-breaker, including corrections spanning pages. Manual change-history queries are bounded at 200 rows; the compatibility list API assembles every page in one transaction without truncation. Ordered ledger and allocation page APIs are available. Full observable UI history lists and selected-history page demand remain step 09; this step does not claim all UI history is already lazy.

## Measured indexes

[Probe](step-08-index-probe.py) and [raw results](step-08-index-results.json) use the exported v16 schema with ten interleaved users, 100 targets/user and 1,000 entries/user. These are synthetic SQLite VM instructions, **not phone milliseconds**.

| Query | Before | After |
| --- | ---: | ---: |
| Active target | 757 | 45 |
| Target allocation page | 16,293 | 4,215 |
| Vow allocation page | 18,631 | 4,215 |
| Unclaimed entry page | 14,644 | 4,615 |
| Ordered 200-row ledger page | 20,872 | 4,210 |

The migration adds `(userId,isActive,isCompleted,startedAtMillis,stableId)` on targets, replaces the single target/vow allocation indexes with `(userId,targetId,id)` and `(userId,vowId,id)`, and adds `(userId,createdAtMillis,id)` on the ledger. Android query-plan tests verify all four indexes and absence of temporary sorting trees for representative queries.

Seven synthetic write samples, each 1,000 inserts plus a user-wide quantity update, had median 5.94 ms before and 6.60 ms after: roughly 11% overhead. This includes Python fixture construction and excludes journal triggers; it isolates index cost rather than end-to-end save time. The query reductions justify these four indexes. No further session-page, backup-change or mixed-outbox index was added: broad capture pages were retired and existing session/audio uniqueness indexes already serve association lookup. The remaining UI/period-history candidates should be measured with step 09's query shapes.

## Verification

- **598 unit tests pass:** 363 domain, 137 data and 98 app; zero failures/errors/skips. Debug app and data-test APK builds and app lint pass with no errors.
- **56 Pixel 7 Pro Room tests pass:** five new changed-record/work tests plus accounting, ownership, migration, backup validation, scheduling, initialization and immutable session/outbox regressions. Migration coverage includes all v1–v16 starts through schema validation and production reopen.
- **Work counts at 0/1,000/10,000 sessions plus the same number of ledger entries:** after bootstrap, a new correct session encodes exactly **two** payloads (entry + session), independent of unrelated history size. Five unchanged capture/resume and goal-projection checks per size encode **zero** payloads, issue **zero** session/ledger-history reads and write **zero** goals. The pending-identity query itself remains bounded. These are database/encoding counts, not frame-time or billed-read measurements.
- **New coverage:** source/outbox rollback, reopen before drain, old-entry correction, coalesced keys, interrupted bootstrap, account relinking, one-time fingerprint normalization, reminder mutations/unchanged values, remote completion metadata, 450-row ordered history and corrections spanning pages. Existing tests retain exact large totals, timezone allocation, completion reversal, conflicts and either restore order.
- **Eight cloud device tests pass** against isolated local Firebase demo emulators: canonical counting repair/replay/concurrency, backup quarantine/conflict recovery, remote-only discovery, offline Firestore reconnect, manual pull, refresh during restore and account isolation. The first discovery run hit Android's unvalidated-Wi-Fi gate although ADB loopback was reachable. The discovery fixture now supplies a loopback-only connectivity check; production still requires validated internet. Tests retain real Firebase clients, Room, retry gating and SDK network disable/re-enable.
- **Actual process death passes:** the data test process kills itself immediately after commit; a separate process recovers its session, accepted entry and event and retries without duplication. `Process crashed` is the expected first-phase result.

Retained evidence: `build/perf-08-final-verification.log`, `build/perf-08-final-build.log`, `build/perf-08-room-device-final.log`, `build/perf-08-work-counts.log`, `build/perf-08-metadata-regressions.log`, `build/perf-08-cloud-device.log`, `build/perf-08-emulators.log`, `build/perf-08-crash-save.log`, `build/perf-08-crash-recover.log`, Gradle XML reports and the exported Room v17 schema. The work test asserts every fixture size; the final logcat buffer retains the 10,000-row result and Android plans.

No Firestore rules changed or production deployment occurred. Initial verification installed only the isolated data-test APK. At the user's subsequent request, the debug app (version 2.0, code 12) was installed on the Pixel 7 Pro via ADB on 12 September 2026, preserving existing app data. Step 06's production rules prerequisite and step 13's two-physical-phone/release-profile matrix remain unchanged.
