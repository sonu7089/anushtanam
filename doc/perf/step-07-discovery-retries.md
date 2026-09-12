# Step 07 — remote discovery and bounded retries

Implemented and locally verified on 12 September 2026. This resolves DB-05, POLL-03's pre-claim retry path and the sync-clock portion of POLL-06. No Firestore rules, production accounts or production app installation were changed.

## Remote discovery and manual refresh

[CloudSyncDiscovery](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncDiscovery.kt) observes the newest document in the existing account-owned `backupChanges` feed, with `limit(1)`. Only server-confirmed snapshots without pending writes become discovery signals. The document ID and server timestamp form a persisted, deduplicated signal; record contents still pass through the existing incremental restore and validation path. This adds no cloud collection, writer protocol, rule or index requirement. It bounds the listener to one existing envelope, rather than claiming that envelope has a newly reduced payload size.

[CloudSyncCoordinator](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncCoordinator.kt) owns the listener for the visible account. Leaving the foreground, signing out or changing accounts cancels it. Foreground entry also requests recovery, covering changes while detached; repeated entries share the attempt gate. Terminal listener failures use capped, jittered retries and retain recovery work. Late snapshots/errors for an old account cannot schedule work for the new account. An unchanged foreground account has no periodic discovery query or full-history network poll.

`requestRecovery` now marks even a settled account for a pull. The Settings sync button and ViewModel permit an idle manual refresh, with the same active-attempt/cooldown checks as queued work. A manual request overlapping another request or a background job remains durable. A pending request carries a token in the existing Room queue-state table; restore clears `restorePending` only if its starting token is still current. This preserves a remote notification or manual refresh arriving during a pull, including across process removal.

Remote changes while the app is fully backgrounded are discovered on the next foreground entry. Already-known work retains the persistent network-constrained Android job; this change does not promise immediate background push delivery.

## Retry and clock behavior

[CloudSyncRetryGate](../../core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncRetryGate.kt) replaces the former wall-clock-only attempt claim. The runner claims before scanning/capturing local history, checking connectivity, uploading or restoring. Room invalidations and foreground entry no longer directly invoke unthrottled capture. The existing full capture remains as a safety net for step 08.

- Normal attempts have a minimum 60-second interval. An initial manual request can skip the initial upload delay, but cannot bypass a previous attempt or failure deadline.
- Failures, including capture and timeouts, retain work and attention state. The first failure waits 60 seconds; repeated failures use exponential ceilings of 2, 4, 8 and then 15 minutes, with jitter between half the ceiling (at least 60 seconds) and the ceiling. Success resets the failure count. A known offline connection retains the normal minute gate without discarding work.
- The deadline and failure count are persisted atomically with the existing schedule/last-attempt records, using the existing `backup_queue_state` table. There is no schema migration.
- Within the same boot, persisted `elapsedRealtime` plus Android's boot count makes forward/backward wall-clock changes irrelevant to the cooldown, including process restarts. After reboot, the persisted wall deadline is bounded to 0–15 minutes and rebased once to elapsed time. Implausibly distant elapsed deadlines are also rebased once. Legacy future wall timestamps are rebased to one minute, so repeated checks cannot clamp them forever.
- An in-process emergency deadline is installed before schedule IO. Persistent failures reading/writing that schedule cannot cause repeated capture attempts in the same process. A fully unavailable database cannot provide new durable state; the Android job and foreground recovery remain the backstops.

The foreground retry loop and Settings readiness use the same gate. The runner mutex, UID checks, transactional acknowledgement and record-level validation remain intact. Android may defer background jobs beyond the application's requested deadline.

The API choices follow Android's [elapsed-time clock documentation](https://developer.android.com/reference/android/os/SystemClock) and Firebase's [listener metadata, removal and terminal-error behavior](https://firebase.google.com/docs/firestore/query-data/listen).

## Verification

- **235 unit tests passed:** 137 data and 98 app, zero failures/errors/skips. New cases cover idle manual pulls, capture failure with 100 immediate retry requests, schedule IO failure, elapsed-time cooldown after wall jumps/process restart, reboot/legacy-clock reconciliation, account isolation and capped jitter/reset. Existing upload acknowledgement, timeout, cancellation and shared-request tests remain green.
- **10 Pixel 7 Pro tests passed:** two new discovery/recovery scenarios, five Room scheduling/reopen regressions and three existing backup/migration/quarantine/conflict tests. Two independent Firebase clients and Room replicas verify remote-only arrival, network disable/reconnect, idle manual pull, listener detachment, account switch and a new request during restore finalization. These tests advance the injected retry clock where appropriate; they do not change the phone's system clock.
- **5 disposable Android emulator tests passed:** idle sync-button availability, disabled active/cooldown behavior, quarantine retry, conflict UI, and persistent network-constrained job registration.
- **Cross-device check:** an independent emulator sender commits/uploads a 72-japa entry while the Pixel receiver waits with an empty local history and no new local chant. The receiver uses the real remaining retry duration after setup. The receiver completed in 61.699 seconds and the sender in 0.673 seconds; both tests passed. The paired instrumentation results are retained separately.
- **Build/lint:** debug app and instrumentation APKs compile, and app lint reports no errors. The updated UI was installed only in the disposable emulator; the phone received only the isolated `com.mantrasiddhi.data.test` test APK.

Retained evidence: `build/perf-07-final-verification.log`, `build/perf-07-device-final.log`, `build/perf-07-ui-device.log`, `build/perf-07-cross-send.log`, `build/perf-07-cross-receive.log` and `build/perf-07-emulators.log`, plus Gradle XML test reports. `RemoteDiscoveryTest` and `CrossDeviceDiscoveryTest` restrict cloud testing to local demo emulators. For the paired check, start `CrossDeviceDiscoveryTest#receive` on one device with `crossDeviceRole=receive` and a unique `crossDeviceEmail`, wait for `REMOTE_RECEIVER_READY`, then run `#send` on the other with the same email and `crossDeviceRole=send`; both use the Firebase emulator instrumentation arguments.

An initial account-switch probe exposed the late-listener error, now covered by the passing regression. A test that assumed an explicitly disabled Firestore network would leave a server query suspended was corrected: the SDK can fail that query immediately. The initial cross-device process was terminated by Android backup (SIGKILL); the paired check was rerun after backup completed. The disposable emulator initially contained an old fixture database; UI verification used a cleared disposable app fixture.

## Remaining release limits

Step 06's rules must still be deployed before this build is distributed against production; this task performed no deployment. The cross-device test uses one physical phone and one Android emulator with local Firebase, not two physical phones or production Firebase. The two-physical-phone/release-profile matrix remains part of step 13. Calendar/UI clock handling remains in steps 09/11, and broad capture/query work remains step 08.
