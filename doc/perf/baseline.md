# Step 00 — baseline environment and measurements

Status: step 00 complete for the reference baseline below. Broader audit acceptance scenarios remain open; no optimization fixes are included.

## Repeatable setup

Use JDK 17 or newer, the Gradle wrapper, Android platform 36 and Build Tools 35.0.0. This host already has these packages; the earlier missing-SDK/license errors were caused by sandbox access restrictions. Builds succeed with access to the configured SDK and Gradle cache. No SDK downgrade or new license acceptance was needed.

The `benchmark` variant inherits release R8/resource shrinking, is non-debuggable and shell-profileable, and uses the debug signing key. Its application ID is unchanged (`shastralife.anushtanam`); install only on a test device. Do not uninstall an existing production app or clear its data to resolve a signing mismatch. The snapshot receiver exists only in this variant and requires the shell's DUMP permission. Other builds leave work probes disabled.

Prepare a disposable account and record role, child count, history size, cache/network state and reminder settings without committing personal data. Complete permission prompts before steady-state measurements; measure first-install interruptions separately.

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleBenchmark :macrobenchmark:assembleBenchmark
adb -s SERIAL install -r frontend/android/build/outputs/apk/benchmark/app-benchmark.apk
.\scripts\perf-baseline.ps1 -Serial SERIAL -AccountState welcome
# Repeat after signing into a disposable account on-device:
.\scripts\perf-baseline.ps1 -Serial SERIAL -AccountState home
# Independent test process; never modifies the application database:
.\scripts\perf-baseline.ps1 -Serial SERIAL -HistoryOnly
# Diagnose one size in a fresh test process if a combined run fails:
.\scripts\perf-baseline.ps1 -Serial SERIAL -HistoryOnly -HistorySize 10000
# Independent first-emission samples; does not replace retained-process stress:
.\scripts\perf-baseline.ps1 -Serial SERIAL -HistoryOnly -FreshHistoryProcesses -Iterations 20
```

Use `-Iterations 1` for a smoke check and at least 20 for evidence. Emulator runs require `-Emulator` and must stay separate from physical results. Keep the phone unlocked, idle and on the same refresh/density/font/animation settings. Compare thermal state before and after runs.

## Measurement boundaries

- Cold/warm/hot launcher startup uses Macrobenchmark `CompilationMode.None` with startup/frame metrics. The driver sends a MAIN/LAUNCHER/new-task/reset-task intent and waits for explicit ready tags: the library's `startActivityAndWait` gfxinfo waiter can race the short-lived Splash window. Cold/warm starts also set CLEAR_TASK to recreate activities; hot starts reuse them. Macrobenchmark controls process killing and excludes its warmup pass from both library and custom results. UI Automator's implicit accessibility-idle wait is disabled; animated content need not become idle to be usable. A Compose fully-drawn reporter waits for laid-out Welcome or loaded Home. Raw observer timings span the launcher call through usable-content detection, because the separate Splash activity can make first-frame metrics misleading. UI Automator still adds observation overhead; these are not exact frame timestamps.
- The versioned `macrobenchmark/src/main/assets/baseline-perfetto.pbtxt` captures startup, frame timelines, scheduling, process and memory events. It limits app atrace tags to the app/driver and excludes SDK composition tracing. The library's default global `track_event` source waited 30 seconds for unrelated Chromium producers on this phone; the explicit configuration starts with exit code 0 instead of timeout code 2. Keep this configuration identical for comparisons. Interrupted harness-diagnostic runs are not baseline evidence.
- Authenticated navigation visits Progress → Settings → Home and includes transitions in frame capture. Settings readiness means its core local page is present, not that cloud refresh finished. Practice, Family, Messages and History-tab journeys need separate state-specific scenarios.
- Idle samples span 30 seconds after 10 seconds of settling. Signed-in runs retain visited Progress/Settings destinations. CPU is process CPU milliseconds / elapsed milliseconds, expressed as percent of one core, including probe overhead. Query/subscription deltas require the same PID.
- Work snapshots count Room statement executions by operation/table, full-session mapping rows and account/full-session/japa producers. These are application work counts, not billed reads or every SDK listener. Bind arguments and account identifiers are excluded. Compare the same probe configuration across revisions.
- `HistoryBaselineTest` seeds isolated on-disk 0/1,000/10,000-session databases: deterministic IDs, 14 word scores, a 365-day distribution, incorrect/practice attempts, and both ledger-backed and legacy accepted audio. It runs real domain analytics and japa aggregation over real Room queries, collecting downstream on Main. A read-only adapter replaces the sync-dependent repository constructor. This measures local analytics, not authenticated navigation or a complete ViewModel. It asserts exact de-duplication totals and cancellation cleanup without freezing the current query count as required behavior. Databases are deleted in `finally`.

Fixture version 1 uses the fixed anchor `2026-09-12T06:00:00Z`. Preserve it across comparisons; add a separate versioned fixture for current-day/midnight cases. These local test histories do not seed a cloud account. They run in a debug instrumentation APK against freshly seeded, already-open Room databases; their timings describe analytics scaling, not optimized app page latency. CPU milliseconds include all process threads and can exceed wall milliseconds.

## Evidence and completion

The runner builds with Gradle, installs with ADB and invokes instrumentation directly, preserving the installed app/account and raw files instead of Gradle connected-test cleanup. Notification permission is granted before app measurements. It stores environment/thermal details, logs, raw JSON, APK hash and Macrobenchmark trace outputs under `build/perf-baseline/<timestamp>`. Retain raw files with each run; commit aggregate evidence, not personal device dumps. Report median and nearest-rank p95 for raw startup/navigation samples and frame-overrun distributions from Macrobenchmark. Distinguish first visits, revisits and skipped tests. Run `python scripts/summarize-perf.py build/perf-baseline/RUN` to aggregate a run.

History results are saved atomically after each completed sample, outside its measured interval. `complete: false` means a partial/failed run, even if some timings exist. The runner checks the JUnit success footer because `adb shell am instrument` can exit zero after a process crash.

History measurements are opt-in (`perfBaseline=true`, supplied by the runner) and skip in ordinary connected functional-test suites. `-FreshHistoryProcesses` runs one first-emission sample per selected size per test process, in 0 → 1,000 → 10,000 order by default, rebuilding the deterministic histories each time and retaining all outputs. Keep this separate from multiple emissions in one retained process: a successful fresh-process baseline cannot dismiss a retained-process crash.

Physical reference: Pixel 7 Pro, Android 17 beta (`CP41.260814.003.A2`), 1440 × 3120, density 560. This does not establish performance on stable OS versions or lower-end hardware.

The pre-change domain/data/app unit suites passed on 12 September 2026 (`build/perf-00-unit-tests.log`). Harness verification and device results follow below. Step 00 has recorded startup, navigation, idle work and representative histories; this closes baseline setup, not the complete device-validation matrix. Two-device cloud recovery belongs to later correctness/closure work.

The harness follows Android's [Macrobenchmark guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview). App-generated Baseline Profile optimization is deferred until after this compilation baseline is captured.

### Verification on 12 September 2026

- Optimized benchmark app, self-instrumenting Macrobenchmark driver and history test APK build successfully. Benchmark BuildConfig has `DEBUG=false` and `PERF_PROBES=true`; ordinary debug has probes disabled. Only the benchmark manifest contains the shell-protected snapshot receiver.
- 541 domain/data/app unit tests pass, with zero failures/errors/skips (`build/perf-00-verification.log` and the module XML reports).
- 22 existing `JapaAccountingDatabaseTest`, `SandhyaCountDatabaseTest` and `CloudSyncDatabaseTest` cases pass on the Pixel (`build/perf-00-device-correctness.log`). This is not a claim that all migrations or cloud recovery scenarios were exercised.
- All three isolated history sizes pass a one-sample smoke run. Seven SELECT executions are observed per initial analytics emission; five entity-to-domain session lists materialize 0/5,000/50,000 rows, plus the japa repository's raw-session read. Repeated retained-process runs abort natively during the 10,000-session stage. A captured HeapTaskDaemon stack ends in C++ allocation failure during ART `MarkCompact::MarkRoots`/`ThreadList::RunCheckpoint`; forcing AOT compilation did not prevent the failure. This identifies the failing runtime path, not its root cause. Preserve it when investigating later optimizations. Raw smoke output: `build/perf-baseline/pixel-data-smoke`; partial repeated outputs: `20260912-121952` and `pixel-history-aot`; native stack: `build/perf-00-history-native-crash.txt`.
- Startup/idle smoke tests pass with signed-out navigation explicitly skipped. The first runner lost custom files during Gradle cleanup; its Perfetto traces and library metrics were retained in `build/perf-baseline/20260912-120820`. Subsequent runs use the corrected ADB runner. Do not treat this smoke check as a 20-sample baseline.

### Returning-account app baseline

Run `20260912-124121` passes all five instrumentation tests and retains 80 Perfetto traces, library metrics and custom JSON. It uses the user-confirmed disposable account and its existing local data. Account role, linked-child fixtures, reminder settings and network conditions were not controlled. The app APK SHA-256 and aggregate evidence are recorded in [baseline-results.json](baseline-results.json).

The Pixel was USB charging, with thermal status **2 before and after** the run (battery temperature 39.7 °C at entry). Treat these as reference measurements under those conditions, not cooled-device performance budgets. Animation scales were 1.0. Do not compare to another device, OS or thermal state as an optimization claim.

| Observed journey | Samples | Median (ms) | p95 (ms) |
| --- | ---: | ---: | ---: |
| Cold launcher → usable Home | 20 | 4,757.683 | 5,367.152 |
| Warm launcher → usable Home | 20 | 2,943.151 | 3,072.445 |
| Hot launcher → usable Home | 20 | 86.339 | 95.113 |
| Home → Progress | 20 | 304.322 | 368.812 |
| Progress → Settings | 20 | 320.800 | 403.677 |
| Settings → Home | 20 | 346.079 | 463.786 |

Cold startup's library TTID median is 254.933 ms, while fully drawn median is 4,568.629 ms. Warm TTID/fully drawn medians are 46.842/2,861.214 ms. The splash's first frame substantially precedes usable Home. The observer and fully drawn measurements have different boundaries; do not subtract them to infer one component's cost.

Across the navigation journey, 2,568 frame timing samples have p95 CPU frame duration 9.521 ms and p95 frame overrun 6.867 ms; 6.931% have positive overrun. This is a pooled frame-overrun measure, not a separate Android jank classification. Startup frame distributions are in the JSON. Hot starts expose only 19 measured frame samples across 20 launches and do not establish scrolling performance.

After visiting Progress and Settings and settling for 10 seconds, the same app process used **217 CPU ms over 30,066 elapsed ms (0.722% of one core)**. All observed Room-query, mapped-row and subscription-start deltas were zero. Active producers remained **auth.account = 3, sessions.full = 1, japa.history = 1** at both snapshots. Before settling, the process had executed nine recitation SELECTs and materialized six session rows across six full-session producer starts. These observations do not establish zero network traffic or zero work in uninstrumented paths.

### Representative history baseline

Run `20260912-125033` passes all **20 fresh instrumentation processes**, with one emission for each size per process (60 measurements). Every sample verifies exact accepted-japa totals, ledger/legacy de-duplication and zero active fixture/japa collectors after cancellation. Thermal status was 2 at entry and exit. An ordinary invocation without `perfBaseline=true` skips the workload before seeding (`build/perf-00-history-opt-in.log`).

| Sessions | Samples | Wall median / p95 (ms) | CPU median / p95 (ms) | SELECTs per sample | Mapped session rows |
| --- | ---: | ---: | ---: | ---: | ---: |
| 0 | 20 | 86.741 / 99.644 | 263.5 / 291 | 7 | 0 |
| 1,000 | 20 | 1,007.999 / 1,080.859 | 4,211 / 4,509 | 7 | 5,000 |
| 10,000 | 20 | 8,457.444 / 8,886.794 | 37,850.5 / 40,221 | 7 | 50,000 |

These are debug local-analytics timings, not release navigation measurements. All 60 samples execute seven SELECTs and map five copies of the session list. The sixth session read belongs to japa legacy fallback and is not counted as entity-to-domain mapping. This provides a concrete work baseline for steps 08/09 without prematurely changing query sharing, data demand or accounting behavior.

The separate retained-process 10,000-session crash remains **unresolved**. Fresh-process success does not prove repeated large-history navigation is stable. Keep the partial outputs and native stack listed above; investigate and repeat that stress case when changing history processing. Step 00 records this existing failure rather than treating it as a successful stress test.

### Remaining validation boundaries

The full matrix in [README.md](README.md#device-validation-and-acceptance) still needs signed-out 20-sample runs, other account roles, Family/Practice/Messages and History-tab journeys, slow/offline networks, image scrolling, light/dark/large-font checks, reminders, stable-OS and lower-end devices. Capture the relevant additional baseline before optimizing those paths and repeat it during closure. No app-generated Baseline Profile, performance budget ratchet or before/after improvement is claimed here.
