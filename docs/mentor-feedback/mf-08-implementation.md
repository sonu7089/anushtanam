# MF-08: Automatic practice backup and recovery

Implemented locally on 10 September 2026. The owner confirmed publishing the Firebase rules and seeing a successful sync at 10:38. The background and manual sync follow-up requires no further rules or server fields. Privacy page publication remains a release step.

## What changes in the app

About Anushtanam is removed from Settings. A small Backup and Sync card sits immediately above Sign out, using the existing Lotus card, typography and theme colors. It shows whether practice is saved locally, waiting to sync, syncing, up to date, or taking longer, with the last successful sync time. A simple Sync now button retries pending work. It is disabled when everything is up to date, while sync is running, and during the retry cooldown. There is no file import or list of backup contents.

When two phones change the same entry, Review changed counts opens a quiet choice between the current count and the other version. Both versions are retained. The chosen count becomes a new revision and syncs automatically. A stale review cannot overwrite a newer local edit.

All added app copy uses simple English without em dashes. Existing artwork is reused.

The latest visual follow-up replaces the visible Sync now text with a circular arrows button beside Backup and Sync. The accessible label and all existing enablement rules remain. A small progress indicator appears while syncing. The debug build and three Settings UI checks pass, with light and dark layouts visually reviewed.

## Saved data and recovery

Automatic backup covers every persisted recitation performance field: mantra, practice mode, overall and per-axis scores, per-word results, transcription, correctness, duration, and practice timestamp. It also covers japa contributions and corrections, Sandhya observance, original period change history and milestone feedback, self-set goals and Purashcharana state, including completion timestamps. Performance screens rebuild their statistics from restored sessions. Existing account and family features keep their server records.

Room v13 gives recitation sessions stable identities and adds account-scoped queue fingerprints, restore checkpoints, status and conflict records. The migration preserves existing scores and queued uploads. Session links are rebuilt using stable identities rather than another phone's local row numbers, preventing an audio session and its japa contribution from counting twice.

Room remains durable local storage. Changes are collected automatically, uploads are deferred, and a failed scheduler or interrupted upload does not erase saved practice. Each acknowledged backup operation is removed separately, allowing a slow connection to make progress through a long history. Background retry also continues when uploads have finished but restore is still pending.

A recitation and its performance upload request commit in the same Room transaction. An app-wide sync request survives leaving Settings or closing the activity. A persisted, network-constrained Android job retries after process removal; Android may defer it for battery and idle restrictions. Force-stopping the app prevents background execution until the app can run again.

Manual and automatic attempts share a single runner and a persisted one-minute cooldown. Sync now can skip the initial scheduling delay, but repeated taps cannot queue duplicate attempts or bypass the cooldown by reopening the app. Unchanged records compare by their contents, regardless of Firebase field order. With no pending uploads or unfinished recovery, practice sync makes no Firebase requests and cancels its background job. First installation recovery is still allowed without local changes.

There is no periodic discovery of remote-only practice changes on an already synced, idle phone. It receives those changes during its next change-triggered sync; a fresh installation also recovers them. Immediate remote discovery without polling would need a future server notification. Existing interactive account and family requests are separate. Client throttling reduces normal app traffic; it is not server-side protection against a modified client.

The server change feed is ordered by a server timestamp and document ID. Downloads use 200-record pages. Each page and checkpoint commit together; the inclusive timestamp boundary safely replays records sharing a timestamp. The initial compatibility scan also keeps page checkpoints. Final period and goal projections use the same accounting rules as local saves. Replaying accepted revisions does not create false conflicts.

The older aggregate-only login restore is removed because it could recreate a goal from stale totals. Existing local test history is automatically queued, so it does not need a manual import or a user-facing legacy migration workflow. This release is intended for the current testing population and current app version on each test device.

No audio file exists to back up: the app does not retain recordings. Device permissions, reminder scheduling, display preferences and unfinished counter drafts remain local; they are not recorded performance or completed japa.

## Server and deletion

The owner-only `practiceRecords`, `backupChanges` and `japaConflicts` collections are added under `users/{uid}`. Detailed performance is not exposed through family links. Feed contents must match their canonical record and use a server timestamp. Session performance records and count-change records are immutable. Observed Sandhya cannot be downgraded by another phone's missed reminder. Accepted and rejected japa revisions are retained without pruning.

The owner published `backend/firebase/firestore.rules` and confirmed a successful live sync. The privacy and deletion source pages now describe full practice and performance storage. The existing manual deletion process must recursively delete the full account subtree, including nested revisions and the change feed; deleting the user document alone is insufficient. The agent did not deploy live rules or the website.

## Verification

- 351 domain tests passed for the base implementation. The follow-up passes all 88 data and 79 app unit tests.
- Debug APK assembly and Android lint pass.
- All 29 Firebase emulator rule tests pass.
- The Android migration and complete recovery test pass against local Auth and Firestore emulators. Two databases with different local profile IDs restore 202 full recitation records, replay downloads without duplication, keep an unknown Sandhya count unknown, resolve competing 540 and 500 edits, update goal totals, and reject restore under a different account.
- Six selected Settings and Google account UI tests pass. Two existing missing-configuration tests are skipped because Google is configured.
- Sixteen selected database regression tests pass. The network recovery test is skipped in that separate offline run; it passed in the dedicated emulator run above.
- Light, dark and conflict layouts were visually checked. The conflict choice was also checked at 150% text size.
- The follow-up passes 11 Android checks across recovery, persistent cooldown, job scheduling, and Settings button behavior. Repeated taps share an attempt, leaving Settings does not cancel it, and unchanged restored data leaves the queue empty. The updated waiting card was checked in light and dark themes, together with the disabled button when up to date.

Run the complete local network test with the dedicated emulator configuration and `scripts/test-backup-recovery.ps1`, as described in the backend README. A check with two physical phones against the deployed backend remains a release step.

Screenshots: [light](../ui-review/settings-backup-light-mf-eight.png), [dark](../ui-review/settings-backup-dark-mf-eight.png), [conflict review](../ui-review/settings-backup-conflict-light-mf-eight.png).

Updated Sync now screenshots: [waiting light](../ui-review/settings-backup-waiting-light-manual-sync.png), [waiting dark](../ui-review/settings-backup-waiting-dark-manual-sync.png), [up to date](../ui-review/settings-backup-idle-light-manual-sync.png).
