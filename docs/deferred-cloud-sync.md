MF-08 update (10 September 2026): [automatic practice recovery](mentor-feedback/mf-08-implementation.md) adds full saved performance and history, resumable restore checkpoints, conflict review, and a status card above Sign out. Sync now handles pending work with a shared, persisted one-minute cooldown. Recitations and performance upload requests now commit atomically, and active requests survive leaving the activity. Practice sync stops contacting Firebase when there is no pending upload or recovery. It supersedes the earlier aggregate-only backup, login restore, and non-atomic session-save descriptions below.

# Deferred cloud sync — 6 September 2026

Recitations and local counters no longer wait for cloud access. Progress, self-target changes, Purashcharana snapshots, and child-goal increments enter a durable Room outbox. Only the current signed-in account's pending work is eligible for upload.

## Timing and responsiveness

- The first attempt is due 60 seconds after the first queued change. Further recitations do not push that deadline back.
- While the app is visible and work is pending, a foreground loop checks every minute. A persisted database deadline prevents foreground and background runners from uploading more frequently.
- Upload requires a validated internet connection. Offline and failed attempts leave the batch queued for a later check. Each upload wait is bounded to 20 seconds.
- Android also receives one persisted, network-constrained JobScheduler job. It is rearmed only when work remains. Android may defer background execution for battery, quota, or idle restrictions; exactly one-minute background execution is not guaranteed. Force-stopping the app prevents background work until Android permits it again after reopening.
- With no pending changes for the current account, the sync timer and scheduled job stop. Existing realtime reads and interactive account/family operations retain their existing behavior; they are not periodic outbox uploads.
- The Progress screen overlays unacknowledged local changes on the cloud summary. This projection runs off the main thread. Pending target and Purashcharana changes prevent stale cloud state from being restored over a local completion or withdrawal.

The scheduler uses Android's existing framework APIs; no dependency or backend deployment is required. See [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder).

## Persistence and safe retries

Room schema 9 adds an ordered outbox and per-account next-attempt times, with a non-destructive v8→v9 migration. Enqueued changes survive process death and database reopening. A batch contains at most 100 ordered changes; a large offline backlog drains in subsequent minute checks.

One Firestore transaction applies each batch, combines changes to each document into one write, and records its checkpoint in `users/{uid}.syncCheckpoints`. Retrying a batch after the server committed but the app missed its acknowledgement skips already applied changes. Room deletes only the acknowledged IDs, preserving changes queued during the upload. Progress also stores its checkpoint in its own document so local projection avoids double counting.

The checkpoint stream identity is stored outside Android backup. Restored queued records retain their original identities, while newly queued changes use the new installation's identity. Account switching does not upload another account's changes or discard them; they resume when that owner signs in again.

Existing scoring, acceptance thresholds, seed approximations, and Purashcharana reconciliation rules remain unchanged. Cloud-backed family goal completion becomes visible after synchronization. Local recitation persistence and outbox enqueue are separate operations: a process kill between those operations is not an atomic recovery guarantee. Clearing app data or uninstalling before upload removes local-only work.

## Verification

Verification passed 338 domain tests, 55 data tests, 42 ViewModel tests, and 29 emulator tests: **464 total, no failures or skips**. Debug/release builds pass; lint reports 0 errors, 12 existing warnings, and 2 hints.

Tests cover the one-minute gate, offline reconnect, upload failure/timeout, restarting a runner, account isolation, batching, replay after lost acknowledgement, progress seeding, cleared targets, and Purashcharana merging. Android database tests cover persisted queues/deadlines, concurrent claims, preserving newly enqueued records, and migration with existing history.

Build/test logs: `build/deferred-sync-checks.log`, `build/deferred-sync-validation.log`, `build/deferred-sync-final.log`, and `build/deferred-sync-goals.log`. The updated debug APK also installed and cold-launched successfully on the Lotus emulator; the idle app had no cloud sync job queued. Signed-in round trips against the live Firebase project and real microphone timing on the owner's phone remain device checks; no live cloud test data was written.

## APKs

- Root `app-debug.apk`: SHA-256 `993A456D166D2B2A38E502A2368596A46069328F3C1FB82FE2011C6A5A634988`.
- `frontend/android/build/outputs/apk/release/app-release.apk`: SHA-256 `FCD82C02D61BA7090841EFD81184DCE6AB39398A2D66B3A4948D2D88E9640A3B`.
