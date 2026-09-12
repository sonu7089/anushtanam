# Step 13 — Final audit verification

**Closed on 12 September 2026 at the user's request:** “I think this is enough for validation lets close this.” Remaining measurements were stopped; the completed evidence below is accepted for this audit closure. This is not a claim that the known limits disappeared. [Aggregate results](step-13-results.json) preserve failed, interrupted and unexecuted work explicitly.

The final verification pass uses the Pixel 7 Pro and, at the user's request, a disposable Android emulator for the second recovery replica. This is local verification, not a production deployment or a two-physical-phone release certification. Raw evidence lives in `build/step13/` and the timestamped `build/perf-baseline/` runs. Aggregate measurements and remaining acceptance gaps are recorded below.

## Verification scope

The existing unit suites cover counting, ownership, account gates, retry clocks, calendar deadlines, playback precision and UI state. Physical Room tests include legacy migration starts, identity repair, quarantine, changed-record capture and the 0/1,000/10,000-row query fixtures. Cloud tests use only local demo Firebase projects and isolated test databases. Neither app history nor production cloud records are cleared for these tests.

The optimized app retains the baseline's release shrinking, `CompilationMode.None`, Perfetto configuration, explicit readiness tags, 20 repetitions and existing returning-account fixture. The original account's exact history/role/network conditions were not controlled in step 00; this rerun cannot retrospectively turn that baseline into a controlled account experiment. Large-history Room measurements remain separate from optimized app navigation.

## Correctness and recovery evidence

- **631 unit tests pass with fresh execution:** 367 domain, 147 data and 117 app, zero failures/errors/skips. Initial Gradle verification reused domain/data results; `build/step13/units-rerun.log` explicitly reruns all three test tasks.
- **60 distinct backend tests pass:** all 56 tests in `backend/firebase/tests/`, plus four audit rejection probes. The performance subset runs 30 overlapping backend tests and those four probes; its 34 tests are not added again to the total. Logs: `backend-all.log` and `rules.log` under `build/step13/`.
- **64 Pixel data tests pass** (`data-device.log`), including every v1–v16 migration start through v17, identity/ownership/counting constraints, exact japa de-duplication, quarantine, changed-record work, visible-query bounds and account sharing. Opt-in history, cloud and process-kill workloads are run separately.
- **Eight Pixel cloud regressions pass** (`recovery/cloud.log`): canonical count replay, concurrent upload, second-replica restore, backup recovery, offline/reconnect, idle manual pull, account switch and refresh during restore.
- **Actual process-death recovery passes** (`recovery/crash-save.log`, `crash-recover.log`): the isolated test process kills itself after the session/outbox transaction commits; a fresh process verifies one credit, its durable event and idempotent retry. The expected crash is injection evidence, not a passing JUnit save-phase test.
- **Both cross-device directions pass** (`recovery-final/`): an empty Pixel receives 72 japa from the emulator in 63.535 seconds; an empty emulator receives 72 from the Pixel in 61.152 seconds. Each asserts exactly one entry. The recorded real retry waits are 58.466 and 58.118 seconds. No injected clock advance is used during either measured transfer wait; the existing setup helper advances its test clock while settling the initial replicas.
- **82 emulator app tests pass** (`ui-emulator.log`). The opt-in real-account reminder-maintenance test is excluded. This includes notification/job contracts, account forms, recovery status, progressive entry, demand/lifecycle behavior, deadlines, rendering, real MediaPlayer and artwork delivery.
- **75 Pixel app tests pass** (`ui-pixel.log`), including all artwork, real MediaPlayer, rendering, progressive-entry, demand/cancellation and deadline checks. Notification/job cases run on the disposable emulator; the opt-in real-account reminder-maintenance test is excluded from this repeat.
- Debug/optimized app and test APKs build. Lint has **0 errors, 14 warnings and 3 hints**. `build-checks.log`, subsequent driver/test build logs and `apk-hashes.json` identify the artifacts.

The cloud rules copy used in the dedicated emulator has SHA-256 `699ae8ea9cf8353a3c93b7ae8e96ec85cd6dcd400a50272f09779b7914e54a3e`, matching `backend/firebase/firestore.rules`. Testing uses local demo accounts and does not establish what rules are currently deployed to production.

### Failed attempts and harness corrections

The first cross-device attempt timed out because the new PowerShell runner could treat an empty log pipeline as a false `-notmatch` result and start the sender before receiver initialization. A diagnostic retry consequently encountered an account that did not yet exist. The corrected runner casts log content to a scalar string, waits for `REMOTE_RECEIVER_READY` and verifies the receiver is still alive. The receiver now asserts its database is empty before announcing readiness and emits discovery/retry/attempt stages. The final two-direction pass above follows that correction. Original logs remain in `recovery/` and `recovery-diagnostic/`; they are not product-recovery failures or successful transfers.

The first app run, `build/perf-baseline/20260912-211525`, has one cold-start readiness timeout after seven completed cold iterations. Its successful warm/hot/navigation/idle scenarios do not make the complete run pass. No app crash was captured for that timeout. A later attempted shell UI dump conflicted with the active instrumentation's UiAutomation registration; it is also excluded from final timing evidence. The benchmark now captures timeout screenshots/hierarchy using its own existing UiAutomation connection. The original timeout's cause is not established, and a successful rerun must not be used to claim that no intermittent entry failure is possible.

SDK/Firebase sandbox access failures, a stale ADB server that could not authorize the emulator, and orphaned local Firestore processes were test-environment issues. ADB was restarted with the existing host key, and recovery moved to dedicated local ports. No SDK downgrade, app-data clear, or production deployment was used to obtain a pass.

## Same-fixture history results

All **20 retained-process samples per size** pass in `build/perf-baseline/20260912-213134`; all **20 fresh processes**, each measuring all three sizes, pass in `20260912-213440`. These are 120 measured emissions. Every emission checks exact accepted counts, ledger/legacy de-duplication and cancellation cleanup. Both runs ended at thermal status 1; the original step-00 reference was at status 2.

| Sessions | Step-00 fresh wall median / p95 (ms) | Final fresh wall median / p95 (ms) | Final retained wall median / p95 (ms) | Final fresh CPU median / p95 (ms) |
| --- | ---: | ---: | ---: | ---: |
| 0 | 86.741 / 99.644 | 94.684 / 112.374 | 44.018 / 72.888 | 299.5 / 336 |
| 1,000 | 1,007.999 / 1,080.859 | 815.880 / 1,012.086 | 612.272 / 656.552 | 2,849.5 / 3,244 |
| 10,000 | 8,457.444 / 8,886.794 | 6,480.753 / 8,203.767 | 6,787.776 / 7,366.730 | 25,549.5 / 29,332 |

The baseline adapter still deliberately requests full-list legacy domain analytics: seven SELECTs per emission and four mapped session-list copies (0/4,000/40,000 rows), versus five copies in step 00. It does not invoke the new selected-tab ViewModel pipeline. These seconds-long debug timings are **not** the latency of Progress overview. `VisibleDemandQueryTest` separately verifies the production SQL projections, no full-session/word-score materialization for overview, seven-day score bounds and 31-row cursor queries at all three sizes.

The former retained-process native crash was **not reproduced in this final 20-sample run**. Preserve the original native stack and failed step-00 traces; this does not identify the Android beta runtime's original allocation failure or prove stability for arbitrarily long workloads. The empty-history fresh median also increased, so this is not a universal speedup claim.

## Image visual review

The deterministic exporter reproduces **84 resources / 4,500,370 bytes** exactly. The repeated source/derivative review generates **210 comparisons**, with the same worst mean absolute channel difference of **4.891/255**. Inspected source/derivative pairs cover every artwork identity, light/dark surfaces, small icons, book/bead edges, stems, alpha edges and the high-density book/jasmine placements. No new crop, color or transparency regression was observed.

All **24 Pixel screenshots** from the original step-10 matrix were captured and inspected: eight pages at 360 dp mdpi/light, 360 dp 4×/dark and 600 dp 3×/light with 1.3 font scale. Welcome, Home cards, Messages, Family, Settings, Purashcharana, Recitation and Guide remain legible, with expected scrolling where content exceeds the viewport. High-density wide photo softness remains limited by the original masters; this pass does not claim new source detail. The 75 Pixel tests also recheck finite size selection, off-Main decoding, immediate memory-cache reuse and usable controls during delayed loading.

Raw screenshots and display settings are in `build/step13/screens/`; comparisons are in `comparisons/`. The physical 1440×3120 size, 560 dpi and 0.85 font scale were restored before the final performance run. A helper command-name collision in the first screenshot attempt was fixed by invoking `adb.exe` explicitly; the complete 24-image pass follows that correction.

## Completed performance measurements and accepted limits

The repeat `20260912-214625` completed 20 cold, 20 warm and 20 hot starts plus idle before the user requested closure during navigation. That run is marked incomplete as a suite; its completed startup samples remain usable evidence. Navigation below comes from the full 20-iteration scenario in the earlier `20260912-211525` run, whose separate cold-start test failed. No partial navigation samples are substituted for a complete scenario.

| Completed scenario | Median / p95 (ms) |
| --- | ---: |
| Cold launcher → usable Home | 2,417.234 / 3,857.641 |
| Warm launcher → usable Home | 230.282 / 332.536 |
| Hot launcher → usable Home | 183.677 / 199.101 |
| Home → Progress | 289.288 / 322.400 |
| Progress → Settings | 278.997 / 317.920 |
| Settings → Home | 268.283 / 335.357 |

The completed navigation scenario contains 2,659 frame samples: CPU duration p95 **8.985 ms**, frame-overrun p95 **1.162 ms**, and **5.566%** positive overrun. The provisional <5% positive-overrun and ≤250 ms cached-navigation p95 targets are **not met**. Those budgets remain accepted limitations, not passing assertions or justification to claim a navigation speedup. Cold/warm startup is much shorter than the step-00 observed medians of 4,757.683/2,943.151 ms, while hot startup is slower than its 86.339 ms reference. These are cumulative, device-specific observations with the fixture/thermal limitations described above.

The completed final idle interval used **0.672% of one core** and recorded zero observed query, subscription-start, registration and reminder-work deltas. Visited Progress/japa producers were inactive. This is not a network-traffic or battery measurement.

The added opt-in `AppBaseline#playbackWork` driver compiles, but its new optimized capture was **not executed** because validation was closed before it started. Retain the [step-12 measured playback/hidden-work evidence](step-12-rendering.md) and this pass's successful real MediaPlayer/rendering device tests; do not describe those as a new playback trace. Likewise, the final navigation repeat was interrupted and is not a new 20-iteration result.

Accepted remaining scope includes the first isolated startup timeout, production rules compatibility/deployment verification, two physical phones, stable-OS/lower-end hardware, the complete account/slow-network/scroll matrix, optimized navigation at all three history sizes, and an app-owned Baseline Profile. These are recorded follow-up opportunities rather than blockers to the closure explicitly requested by the user. No production deployment was performed. The debug APK and original display/screen-timeout settings were restored when the measurement runner stopped.

## Finding disposition index

This index supersedes historical status labels in the original audits; their original source observations remain as evidence of the pre-fix behavior. “Verified locally” does not imply production deployment or completion of every release-device variant.

| Findings | Outcome and implementation evidence | Final verification coverage |
| --- | --- | --- |
| DB-01 | Removed asynchronous demo seeding ([01](database-audit.md#db-01-resolution--step-01-12-september-2026)) | `DatabaseInitializationTest`: fresh, delayed-open and existing-history cases |
| DB-02, DB-07 | Transactional session outbox and canonical exact projections ([06](step-06-recoverable-counts.md)) | Counting unit/Room tests; restart replay, competing uploads, second-replica restore and actual process death |
| DB-03, DB-04, DB-12 | Schema/rules validation, quarantine and bounded exact totals ([02](step-02-validation.md)) | Backend rejection tests; `BackupValidationDatabaseTest`, `BackupRecoveryTest` and counting recovery |
| DB-05 | Foreground feed discovery and idle manual refresh ([07](step-07-discovery-retries.md)) | `RemoteDiscoveryTest`; independent Pixel/emulator sender and receiver in both directions |
| DB-06, DB-11 | Repair before uniqueness; immutable saved assessments and ownership checks ([03](step-03-identities.md)) | `IdentityMigrationTest`, `IdentityContractsTest` and counting/restore regressions |
| DB-08, DB-10, RW-06, RW-07 | Transactional changed identities, bounded backup/projection work and measured indexes ([08](step-08-changed-records.md), [09](step-09-visible-demand.md)) | `ChangedRecordWorkTest`, Room migrations, visible-query bounds and history repeats |
| DB-09, UI-07, RW-09, RW-10, POLL-01, POLL-02, POLL-04 | Shared reminder work; event/deadline calendar and expiry invalidation ([11](step-11-event-deadlines.md)) | Reminder/deadline unit and device tests; steady-state idle work counters |
| UI-01, UI-06, RW-01, RW-08 | Native launcher, shared verified account state and coalesced voice checks ([05](step-05-account-state.md), [10](step-10-startup-images.md), [10b](step-10b-progressive-entry.md)) | Account/gate and progressive-entry tests; optimized startup repeats |
| UI-02 | Sized asynchronous generated artwork ([10](step-10-startup-images.md)) | Deterministic exporter, off-Main decode/cache tests, source comparisons and density/layout screenshots |
| UI-03, UI-04, UI-08, UI-09, RW-02, RW-03, RW-05 | Lazy Home, SQL projections, selected-tab demand and lifecycle ownership ([09](step-09-visible-demand.md)) | Home/Progress demand tests, bounded Room queries and retained/fresh-process histories |
| UI-05 | Cache-first Messages with independent refresh and retained error content ([09](step-09-visible-demand.md)) | Repository/ViewModel unit tests and Messages Compose regressions; live stalled-network release journey remains a separate gate |
| RW-04 | One selected-child pipeline with cancellation and stale-result guards ([04](step-04-child-details.md)) | Child selection/lifecycle unit and device tests |
| RW-13 | Cheap backup counts and conflict review on demand ([09](step-09-visible-demand.md)) | Backup status unit/UI tests and Room count projections |
| RW-11, RW-12, POLL-05 | Narrow player reads, 5 Hz cosmetics, 50 ms precision and one draw-scoped shimmer clock ([12](step-12-rendering.md)) | Real MediaPlayer and rendering tests repeated; step-12 optimized capture retained, new capture not run |
| POLL-03, POLL-06 | Persisted elapsed retry gate and account/lifecycle/deadline ownership ([07](step-07-discovery-retries.md), [09](step-09-visible-demand.md), [11](step-11-event-deadlines.md)) | Clock/reboot/cancellation unit tests, remote discovery and deadline/lifecycle device tests |
| RW-14, smaller cleanup candidates | Chart/background design retained with measured rationale; sorts/formatting removed or memoized ([12](step-12-rendering.md)) | Retain step-12 chart/overdraw evidence; repeat navigation/rendering regressions |
| UI-10 | Benchmark/readiness coverage implemented; motion retained ([00](baseline.md), [12](step-12-rendering.md)) | Navigation latency/jank budgets missed; user accepted the documented limits when closing validation |

## Reproduction

Run the standard Gradle unit/build/lint commands from the repository README and retain their reports. Invoke installed instrumentation directly when preserving app data and raw artifacts matters; Gradle connected-test cleanup can uninstall targets and remove external results.

`scripts/test-audit-recovery.ps1` runs eight cloud regressions, an intentional process-kill/recovery pair and two cross-device transfers. Build/install the data test APK first and invoke the script inside `firebase emulators:exec --project demo-anushtanam`, passing two different ADB serials and the emulator ports. The script checks JUnit success footers, receiver readiness and bounded completion rather than trusting ADB's process exit code. Rules used by the emulator must match the current source hash. No production Firebase configuration is used.

`scripts/perf-baseline.ps1` repeats the app and independent Room measurements. `scripts/summarize-perf.py` reports median, nearest-rank p95 and positive frame overrun; a failed/partial run must never be presented as a passing baseline.

`scripts/review-device-artwork.ps1 -Serial SERIAL` repeats the original eight-page, three-configuration screenshot matrix using the debug design activity and restores display size, density and font scale in `finally`. `scripts/review-artwork.py --output build/step13/comparisons` preserves the original comparison geometry while keeping each run's output separate. Use the pinned Pillow/libwebp runtime for `scripts/generate-artwork.py --check`.

Regenerate the committed aggregate from retained artifacts with `python scripts/summarize-audit.py --app-run build/perf-baseline/20260912-211525 --history-retained build/perf-baseline/20260912-213134 --history-fresh build/perf-baseline/20260912-213440 --stopped-run build/perf-baseline/20260912-214625`. The summary checks requested process/sample counts and JUnit footers, and preserves the distinction between user-accepted closure and a completely passing measurement suite.
