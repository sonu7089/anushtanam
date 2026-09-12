# Step 02 — Cloud validation and resilient restore

Implemented and verified locally on 12 September 2026. **Production rules were deployed to `anushtanam-shastralife` on 12 September 2026 at approximately 13:46 IST, following the user's explicit deployment instruction.** The latest debug APK was reinstalled on the connected Pixel 7 Pro with application data preserved.

Deployment used `firebase deploy --only firestore:rules --project anushtanam-shastralife --non-interactive`. Firebase reported successful compilation, upload and release to `cloud.firestore`. ADB reported `Success` and confirmed version `2.0` / code `12`. Evidence: `build/perf-02-deploy.log` and `build/perf-02-deployment-artifacts.json`. Rules SHA-256: `EFB97FB7F7721308FC4963B16372467556CF30A306104B9288036ED8E6E63D11`; APK SHA-256: `B422972FE766CEC8F0F86B8F3613FAA68791E0808DDF1C0121A94ED6D25B9E0B`.

The deployment does not certify the installed-client inventory or repair existing malformed records. The compatibility plan below records those considerations; no production data rewrite or feed backfill was performed.

## Changes

- Assigned goals now require a complete schema, integer progress in `0..target`, an Int-sized positive target, and completion consistent with the count. Child updates can change only `currentRecitationCount` and `isCompleted`; creation time, participant IDs, rewards, target size and additional fields are protected. Parent writes also validate the resulting schema and preserve creation time and participant identity. This remains self-reported progress; it does not prove that an audio recitation happened. Trusted reward accounting remains outside this fix.
- Owner entries, targets, vows, sessions, observance logs, count-change records and conflict revisions validate required fields, types, supported schemas, identities and numeric ranges. Rules check basic date structure; Android additionally checks actual calendar dates, timezones, finite scores (including encoded word scores), source/period relationships and narrowing to Int. The existing unversioned payload is defined as v1; optional `schemaVersion: 1` is accepted, and other versions are quarantined. Writers keep the shipped wire format, including the legacy `word:score` encoding.
- Every canonical entry/goal/practice/conflict creation or change requires a matching restore-feed record with the current server timestamp in the same atomic write. Conflict resolution is covered too. Revision history remains immutable and validates complete entry payloads; a rejected revision need not replace the canonical winner. Feed-to-canonical checks remain in place. This uses Firestore's documented [`getAfter` atomic-write validation](https://firebase.google.com/docs/firestore/manage-data/transactions#data_validation_for_atomic_operations); actual competing-edit transactions pass emulator/device testing within the access-call limits.
- Restore validates each document before admitting it to Room. Invalid records are retained in an account-scoped `backup_quarantine` table with source path, identity, payload and reason. Non-JSON values such as NaN receive a diagnostic representation. Valid rows, quarantine and the page checkpoint commit together. Account changes, cancellation and database failures still abort the transaction rather than being mislabeled as bad records.
- Settings explains that quarantined records leave counts incomplete and enables a manual retry even when uploads are empty. Quarantine does not schedule endless background retries or mark a complete backup success. Each restore retries at most 20 quarantined source documents, with a persisted rotating position. Repairs behind the ordinary feed cursor are re-fetched. A deleted source remains retained for support review rather than being silently discarded.
- Room v14 adds quarantine without rewriting user history or pending uploads. The v13→v14 migration schedules one canonical compatibility scan, clearing only old bootstrap checkpoints. That scan now includes practice records and conflicts as well as entries and goals, including records from before the feed existed.

Implementation: [validator](../../core/data/src/main/java/com/mantrasiddhi/data/sync/BackupRecordValidator.kt), [record admission](../../core/data/src/main/java/com/mantrasiddhi/data/sync/BackupRecordRestorer.kt), [restore orchestration](../../core/data/src/main/java/com/mantrasiddhi/data/sync/JapaCloudSync.kt), [rules](../../backend/firebase/firestore.rules), [migration](../../core/data/src/main/java/com/mantrasiddhi/data/local/MantraSiddhiDatabase.kt).

## Count representation decision

An editable manual period has an **exact nonnegative Long total**, `0..9,223,372,036,854,775,807`. Local input and additions already enforce this bound. Restore checks the prospective sum with `BigInteger` before applying a winning entry revision, using the existing user/date/period index. An overflow-causing record stays in quarantine; its full quantity is preserved and the other dates/history continue to restore. No performed quantity is clamped, wrapped or truncated. The accepted subset can differ while a period is over the supported bound, so Settings explicitly labels the restore incomplete until correction. This is not a converged oversized total.

Correction on the source device, or a verified administrative repair, can reduce the period into range. A later retry then admits the deferred record. A correction to an already accepted entry can also free capacity for another deferred entry. Local edits after recovery still use exact quantities and the existing stale-revision protection. Goal display caps and wider lifetime aggregation are unchanged.

Numeric boundaries: quantities/baselines are nonnegative Long; revisions are positive and below `Long.MAX_VALUE` (reserve the next increment); target counts and aksharas must fit their positive Int models; timestamps fit epoch milliseconds from 1970 through 9999; assessment scores are finite in `[0,1]`. Rules cannot enforce an aggregate across arbitrarily many entries or recognize the complete timezone database, so Android validates those conditions before merge. Valid per-entry cloud integers can therefore require aggregate quarantine.

## Compatibility and deployment plan

The repository's version name/code is still `2.0`/`12`; that label alone cannot identify whether a test device has the feed-capable build. Inventory the actual installed artifacts before rollout. This change does not claim that all installed clients or existing cloud records have been audited.

| Client/data state | Compatibility and action |
| --- | --- |
| Current feed-capable v1 writer with complete valid payloads | Compatible with the stricter rules. Canonical/feed/revision/conflict paths pass the device transaction test. |
| New v14 reader while older rules remain deployed | Can quarantine malformed remote input. Ship this reader before stricter rules. |
| Older writer that omits the feed or required fields | Its writes will be denied. Upgrade every such testing device first, or design a trusted server feed bridge as separate deployment work. Do not relax the new checks on the assumption that old clients are gone. |
| Existing canonical record without a feed | New v14 bootstrap can discover it. Backfill its feed before deployment so already bootstrapped devices can discover it too. Rules do not retroactively publish historical changes. |
| Existing malformed or future-version document | Preserve/export it and repair only from verified source data, or keep it quarantined. Do not invent dates, zones, participant IDs or counts. Future schemas require an explicit reader upgrade. |
| Invalid values already committed to Room by an older app | Preserve the device database and repair the affected local rows from verified source history before rollout. Admission validation prevents new contamination; this change does not automatically rewrite previously corrupted local history. |

Deployment sequence:

1. Export the affected owner collections, revision histories and feeds; preserve local databases/outboxes on test devices. Inventory assigned-goal documents as well. Record the current deployed rules for rollback.
2. Distribute the reader with v14 quarantine and verify that all writers publish complete canonical/feed transactions. Do not infer adoption from the shared version label. Existing immutable malformed goal/practice records require reviewed Admin repair, not repeated failing client writes.
3. Review historical documents against the v1 schema. Correct only evidence-backed fields, atomically update each canonical document and matching feed with a server timestamp, and backfill missing feeds. Keep unresolved records and any ambiguous count repair for review. Broader counting reconciliation remains roadmap step 06.
4. Run the tests below against the candidate rules and the exact release client. Check a fresh restore and an already bootstrapped device with a correction made on another device, including the quarantined-record retry.
5. Publish rules only after the client/data prerequisites have been met and deployment is authorized. Watch permission denials and quarantine reports. Roll back the rule release if compatibility was misclassified while preserving every pending upload and quarantined payload.

No live Firebase data reads or record repairs were used for the test verification. The subsequent rules deployment is recorded above. The Android cloud tests use a `demo-anushtanam` project on loopback emulators, with two independent Room databases on one physical phone. They do not substitute for a two-physical-device production rollout check or the complete historical migration suite in step 03.

## Verification

| Check | Result |
| --- | --- |
| Data/app unit tests | 188 passed: 109 data, 79 app; zero failures/errors/skips |
| Firebase suite plus original audit probes | 56 passed; original successful-invalid-write probes now require rejection |
| Room and Firebase recovery instrumentation | 28 passed on Pixel 7 Pro, including five new quarantine/migration tests and three backup recovery tests |
| Backup Settings UI instrumentation | 4 passed, including incomplete-count messaging, retry enablement, cooldown and conflict controls |
| Debug app/data test APKs and app lint | Build passes; lint has 0 errors, 14 warnings and 3 hints |

Focused coverage includes malformed dates/timezones, missing/unknown fields, unsupported versions, nonfinite scores, target/akshara narrowing, cross-account identities, negative/fractional counts, unchanged canonical writes without a fresh feed, immutable field changes, competing revisions, checkpoint rollback, quarantine persistence across reopening, replay, oversized merged counts, correction after overflow, repair behind the cursor, and bounded rotating retry through 21 invalid records. The replica test restores 202 sessions across the 200-document page boundary and verifies count/conflict convergence.

Commands (from the repository root unless noted):

```powershell
.\gradlew.bat :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:assembleDebugAndroidTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
# From backend/firebase: focused rules suite (starts/stops its own emulators)
npm run test:perf
# Or keep dedicated emulators running for device tests:
.\node_modules\.bin\firebase.cmd emulators:start --config firebase.perf.json --only firestore,auth --project demo-anushtanam
```

With those emulators running, the complete backend suite can be run from `backend/firebase`:

```powershell
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:38080'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:39099'
node --test tests/*.test.cjs ../../doc/perf/database-rules-audit.cjs
```

Device verification uses only disposable data-test databases. Installing the app APK with `-r` preserves its application data. Wake/unlock the device before Compose UI tests; the initial dozing/keyguard run found no Compose hierarchy, and the awake rerun passed.

```powershell
adb -s SERIAL reverse tcp:38080 tcp:38080
adb -s SERIAL reverse tcp:39099 tcp:39099
adb -s SERIAL install --no-streaming -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -e firebaseEmulator true -e firebaseEmulatorHost 127.0.0.1 -e firebaseFirestorePort 38080 -e firebaseAuthPort 39099 -e class com.mantrasiddhi.data.local.BackupRecoveryTest,com.mantrasiddhi.data.local.BackupValidationDatabaseTest,com.mantrasiddhi.data.local.JapaAccountingDatabaseTest,com.mantrasiddhi.data.local.SandhyaCountDatabaseTest,com.mantrasiddhi.data.local.DatabaseInitializationTest com.mantrasiddhi.data.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL install --no-streaming -r frontend/android/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install --no-streaming -r -t frontend/android/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -e class com.mantrasiddhi.presentation.BackupStatusUiTest shastralife.anushtanam.test/androidx.test.runner.AndroidJUnitRunner
```

Retained logs: `build/perf-02-final-verification.log`, `build/perf-02-device-build.log`, `build/perf-02-all-rules.log`, `build/perf-02-final-device.log`, and `build/perf-02-ui-device.log`. The SDK/Gradle and Firebase emulator caches required execution outside the filesystem sandbox. No performance improvement is claimed by these correctness checks; production deployment has separate evidence above.
