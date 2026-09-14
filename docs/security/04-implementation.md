# Android security review and implementation

Reviewed 2026-09-14 against the current working tree. The requested `04.md` is
[04-android-application.md](04-android-application.md). That report remains the
historical audit; this file records current decisions and verification.

## Review corrections

- **M-01 applies**, but `allowBackup="true"` is not intrinsically unsafe and Android
  backups are not necessarily unencrypted. Android documents end-to-end encryption
  when supported and configured. The defect was the overly broad inclusion policy.
  Restoring a Firebase session is a risk to test, not an observed exploit in this review.
- The report's suggested database filename is wrong: the app uses `mantra_siddhi_db`.
  SQLite sidecars must accompany it so committed WAL data is not omitted.
- **M-02 remains a residual risk.** SQLCipher encrypts a database, not selected columns.
  A device-bound Keystore key also needs a deliberate backup/restore and key-loss
  strategy. The report says to consider this after fixing backups; no encryption
  migration or transcription deletion is introduced here. Neither would be safe to
  treat as a configuration-only fix. Local history remains protected by the Android
  sandbox and platform storage encryption, without application-level DB encryption.
- **M-03 is defense in depth**, not an unprivileged binding exploit: the signature-level
  `BIND_JOB_SERVICE` permission already guards the service.
- **M-04 describes intentional debug-only behavior**, not an existing release exposure.
- **M-05 applies**, including throwable logging at the reminder warning sites.

References: [Android Auto Backup](https://developer.android.com/identity/data/autobackup),
[JobService](https://developer.android.com/reference/android/app/job/JobService), and
[AGP merged-manifest artifact](https://developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/artifact/SingleArtifact.MERGED_MANIFEST).

## Implemented

- Both backup XML files now allow only `mantra_siddhi_db` and its `-wal`, `-shm`,
  and `-journal` sidecars. Firebase Auth SharedPreferences, notification state,
  DataStore settings/location/mappings, external files, and SDK databases are excluded
  by omission. New files do not silently become eligible.
- Modern cloud backups require encryption capabilities. Device transfer has the same
  file allowlist. Legacy rules require client-side encryption; these are defensive
  compatibility rules because the app's minimum SDK is 33.
- Practice history remains transferable, including transcriptions and profile ownership
  UIDs inside Room. This is not an anonymized export. Settings reset on restore;
  `SyncSignedInUserUseCase` recovers profile ownership from Room by Firebase UID and
  repopulates the DataStore mapping after sign-in. Location must be configured again.
- `CloudSyncJobService` is non-exported and retains `BIND_JOB_SERVICE`.
- `verifyReleaseManifestSecurity` transforms AGP's merged manifest, so APK and bundle
  packaging consume only a verified manifest. It also runs through `:app:check`.
  It rejects enabled/unresolved debug, test-only, or cleartext flags, review activities
  and aliases, and Compose tooling components. A future network security config
  requires extending the check because it can override the cleartext flag.
- Debug source sets retain their intentional development affordances. The ignored
  root `app-debug.apk` was removed; build output directories were not cleaned.
- R8 removes verbose/debug/info logging in optimized builds. Warning/error logging
  remains; cloud-sync and reminder warning sites use constant messages without
  throwable details. The sync retry test now checks this privacy property.

## Verification

Run the policy regressions, release manifest/resource checks and data unit tests with:

```powershell
.\gradlew.bat -I scripts/test-android-security.gradle :app:testAndroidSecurityPolicy :app:verifyReleaseManifestSecurity :app:processReleaseResources :core:data:testDebugUnitTest --console=plain
```

The policy regression task exercises the real Gradle validator with eight valid/unsafe
manifest fixtures, checks the backup allowlists against the database name in source,
and checks encryption requirements. It generates fixtures only under the app build
directory. Release resources must also compile to validate the XML with AAPT.

Result on 2026-09-14: **passed** all eight manifest cases, backup policy checks,
release manifest/resource processing, and all 142 data-layer unit tests (zero failures
or errors). `git diff --check` also passed. SDK access required running Gradle outside
the restricted filesystem sandbox; no SDK/license changes were needed.

## Device verification still required

No Android device/emulator was connected during this review. Before rollout:

1. On a disposable test device/account, create practice history with recent writes,
   configure a location, and sign in. Trigger backup with `bmgr backupnow` and test
   restore onto a clean device, including device-to-device transfer separately.
2. Confirm history and recent writes survive; the Firebase session, location,
   preferences, and notification deduplication state do not. Sign in as the same
   account and confirm the original local profile is reused without duplicate sync.
3. Exercise cloud backup with and without encryption capability and confirm the latter
   does not back up practice history. Existing remote backups are not retroactively
   purged by changing these XML files.
4. Schedule and force the cloud-sync job using `cmd jobscheduler run -f` with the app's
   actual job ID; confirm it completes/retries correctly with the non-exported service.
5. Smoke-test an optimized release build and inspect its logs for prayer schedules,
   account identifiers and backend exception details. No full release APK/R8 runtime
   inspection or physical-device restore is claimed here.
