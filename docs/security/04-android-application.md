# 4. Android Application

[← Back to index](README.md)

> Reviewed and remediated in source on 2026-09-14. See
> [corrections, implementation and device verification requirements](04-implementation.md).
> The findings below describe the original audit, not current closure status.

**Files reviewed:** `frontend/android/src/main/AndroidManifest.xml`,
`frontend/android/build.gradle.kts`, `proguard-rules.pro`, the `core:data` storage and
notification layers, `MainActivity.kt`, and the debug source sets.

## Overall assessment

The manifest is lean — two runtime-sensitive permissions, almost everything
`exported="false"`, no WebView, no custom `ContentProvider`, no exported deep-link
handler. `minSdk = 33` removes a large amount of legacy attack surface. The significant
finding is a single attribute: `allowBackup="true"`.

---

## M-01 — `allowBackup="true"` exports the practice database and location · **HIGH**

```xml
frontend/android/src/main/AndroidManifest.xml:25-28

<application
    android:fullBackupContent="@xml/message_backup_rules"
    android:dataExtractionRules="@xml/message_data_extraction_rules"
    android:name=".MantraSiddhiApplication"
    android:allowBackup="true"
```

Both referenced rule files exclude **exactly one file**:

```xml
frontend/android/src/main/res/xml/message_backup_rules.xml

<full-backup-content>
    <exclude domain="sharedpref" path="teaching_notifications.xml" />
</full-backup-content>
```

```xml
frontend/android/src/main/res/xml/message_data_extraction_rules.xml

<data-extraction-rules>
    <cloud-backup><exclude domain="sharedpref" path="teaching_notifications.xml" /></cloud-backup>
    <device-transfer><exclude domain="sharedpref" path="teaching_notifications.xml" /></device-transfer>
</data-extraction-rules>
```

An `<exclude>`-only ruleset means **everything else is included**. So Android Auto Backup
and device-to-device transfer carry:

| Data | Location | Sensitivity |
|---|---|---|
| Full recitation history incl. **audio transcriptions** and per-word scores | Room DB, `databases/` | High — voice-derived content from a minor |
| Japa entries, targets, Purashcharana vows, Sandhya logs | Room DB | Behavioural timeline |
| **GPS-derived latitude/longitude** | `sandhya_prefs` DataStore, `files/datastore/` | High — child's home location |
| `firebaseUid` → local profile mapping | `user_preferences` DataStore | Identity linkage |
| **Firebase Auth persisted session** (refresh token) | SharedPreferences | Credential material |

The location entry is not hypothetical. `SandhyaPreferences` stores the resolved region
"self-contained (name + lat/lon + zone) so a background alarm/boot receiver can read it"
(`SandhyaPreferences.kt:47-51`) — including GPS-derived regions from the
`ACCESS_COARSE_LOCATION` path.

The Firebase Auth entry is the sharpest. Firebase Auth persists its refresh token in
SharedPreferences. Restoring a backup onto a different device can restore an **already
signed-in session** — no PIN, no password. Combined with [A-01](01-authentication-and-identity.md),
there are now two independent routes into a child's account.

The tell that this is an oversight rather than a decision: the rule files are named
`message_backup_rules` and were plainly written to solve one narrow problem (keeping
notification dedup state out of backups, so a restore does not re-fire old notifications).
Nobody revisited the default for everything else. Note that `TeachingNotifications`
already demonstrates the right instinct elsewhere — it puts the installation ID in
`context.noBackupFilesDir` (`TeachingNotifications.kt:46-49`).

**Remediation.** Invert the rules to an allowlist. Backing up user-generated practice
history is a legitimate product feature, so the goal is exclusion of credentials and
location, not blanket `allowBackup="false"`:

```xml
<data-extraction-rules>
  <cloud-backup>
    <exclude domain="sharedpref" path="." />          <!-- Firebase Auth session -->
    <exclude domain="file" path="datastore/sandhya_prefs.preferences_pb" />
    <include domain="database" path="mantra_siddhi_database" />
  </cloud-backup>
  <device-transfer> <!-- same --> </device-transfer>
</data-extraction-rules>
```

Keep `message_backup_rules.xml` in step for pre-12 semantics, and verify with
`adb shell bmgr backupnow` plus a restore onto a clean device.

---

## M-02 — Practice history is stored in plaintext SQLite · **MEDIUM**

```
core/data/src/main/java/com/mantrasiddhi/data/local/MantraSiddhiDatabase.kt:364-368

return Room.databaseBuilder(
    context.applicationContext,
    MantraSiddhiDatabase::class.java,
    databaseName
)
```

No `openHelperFactory`, no SQLCipher, no `EncryptedSharedPreferences` equivalent. The
database holds, per `RecitationSessionEntity.kt:19-43`:

```kotlin
val transcription: String,      // what the child said, verbatim
val wordScoresJson: String,     // "word1:0.95,word2:0.87"
val overallScore: Double,
val createdAt: Long,
```

plus japa entries, targets, vows and Sandhya logs — a timestamped record of a minor's
daily religious practice, with speech-derived content.

Android's full-disk / file-based encryption protects this while the device is locked, and
app-sandbox isolation protects it from other apps on a non-rooted device. So the realistic
exposure is: a rooted or compromised device, a forensic extraction, an unlocked device in
someone else's hands, and — most importantly — **anywhere the backup in M-01 lands.**

The two findings compound: an unencrypted database is a reasonable default *until* you
ship it off-device.

**Remediation.** Fix M-01 first, which removes most of the exposure. Then consider
SQLCipher with a key held in the Android Keystore for the transcription and score columns
specifically. Also worth asking whether verbatim transcriptions need to be retained at all
after scoring — the scores are what the product uses.

---

## M-03 — `CloudSyncJobService` is exported without need · **LOW**

```xml
frontend/android/src/main/AndroidManifest.xml:34-38

<service android:name=".data.sync.CloudSyncJobService"
    android:permission="android.permission.BIND_JOB_SERVICE"
    android:exported="true" />
<service android:name="com.mantrasiddhi.data.messages.TeachingDeliveryJob"
    android:permission="android.permission.BIND_JOB_SERVICE" android:exported="false" />
```

The two `JobService` declarations disagree. `TeachingDeliveryJob` has it right.

`BIND_JOB_SERVICE` is a `signature|privileged` permission held only by the system, so
`exported="true"` is guarded and **not directly exploitable**. But `exported="true"` is
unnecessary — JobScheduler binds fine with `exported="false"` — and it widens the
component's declared reachability for no benefit. Defence in depth, and consistency
between two components that should match.

**Remediation.** Set `android:exported="false"` on `CloudSyncJobService`. Verify jobs
still schedule and run.

---

## M-04 — Debug variant enables cleartext traffic and an exported activity · **LOW**

```xml
core/data/src/debug/AndroidManifest.xml

<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application android:usesCleartextTraffic="true" />
</manifest>
```

```xml
frontend/android/src/debug/AndroidManifest.xml

<activity android:name="com.mantrasiddhi.review.DesignReviewActivity"
    android:exported="true" android:theme="@style/Theme.MantraSiddhi" />
```

Both are **debug source sets** and do not reach a release build — manifest merging will
not pull `src/debug` into `release`. This is the correct way to scope debug affordances,
and the comment on the review activity ("absent from release builds and the launcher")
shows it was intentional.

Two residual concerns:

1. A **67 MB `app-debug.apk` sits in the repository root** (see
   [S-04](06-supply-chain-and-build.md)). That artifact *does* carry cleartext traffic and
   the exported `DesignReviewActivity`. If it is ever sideloaded or shared, those are
   live. It is gitignored, but it exists on disk and in whatever backups cover this
   working tree.
2. `DesignReviewActivity` being `exported="true"` means any app on a device running the
   debug build can launch it. Fine for a dev handset; worth confirming no debug build is
   ever handed to a tester as if it were a release candidate.

**Remediation.** Delete the stray APK. Add a release-variant assertion — a Gradle check
that fails if `usesCleartextTraffic` or any debug-only component survives into the release
manifest, which is cheap insurance against a future source-set mistake.

---

## M-05 — Release builds retain logging calls · **LOW**

`proguard-rules.pro` contains no `-assumenosideeffects` block for `android.util.Log`, so
these survive minification into release:

```
core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaAlarmReceiver.kt:31
    Log.d("SandhyaReminder", "received $period $stage event=$event at=$now")

core/data/src/main/java/com/mantrasiddhi/data/sandhya/SandhyaScheduler.kt:155
    Log.d("SandhyaReminder", "scheduled $period $stage at ${next.triggerMillis}")

core/data/src/main/java/com/mantrasiddhi/data/sync/CloudSyncRunner.kt:105
    Log.w("CloudSync", "Pending cloud changes retained for retry", failure)
```

The content is genuinely tame — periods, stages and timestamps, no credentials, no
transcriptions, no tokens. Since API 16, apps cannot read each other's logs without
`READ_LOGS`, so exposure needs `adb` access or a compromised device.

Two things still make it worth closing. The Sandhya logs reveal the user's **prayer
schedule**, which is derived from their location and is a behavioural signal. And the
`CloudSyncRunner.kt:105` call logs a full `Throwable` — Firestore exceptions carry
document paths, which include the user's `uid`.

**Remediation.** Add to `proguard-rules.pro`:

```proguard
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```

Keep `w` and `e`, but scrub the throwable at the call site.

---

## Reviewed and found sound

**Permissions are minimal and justified.** `RECORD_AUDIO`, `VIBRATE`, `INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, and an optional
`ACCESS_COARSE_LOCATION`. No `READ_EXTERNAL_STORAGE`, no `READ_CONTACTS`, no
`ACCESS_FINE_LOCATION`, no `QUERY_ALL_PACKAGES`. The manifest comment at lines 11-14
explains the deliberate choice to use inexact alarms specifically to avoid requesting
`SCHEDULE_EXACT_ALARM` — that is data minimisation applied to permissions.

**Component export is otherwise correct.** `MainActivity` is `exported="false"` with the
launcher entry provided by an `activity-alias`, so the app has **no exported deep-link
handler** — the `anushtanam://` scheme is internal-only and cannot be driven by another
app. `SandhyaAlarmReceiver` and `SandhyaActionReceiver` are both `exported="false"`.
`SandhyaBootReceiver` must be exported to receive `BOOT_COMPLETED`; that is required and
correct.

**PendingIntents are safe.** Every one uses `FLAG_IMMUTABLE`
(`TeachingNotifications.kt:197`, `GoalNotifier.kt:125`, `SandhyaNotifier.kt:121`,
`SandhyaScheduler.kt:151`), and the wrapped `Intent` is **explicit** via
`setClassName(context.packageName, ...)`. There is no PendingIntent-hijack or
intent-redirection surface. `Uri.parse(destination)` at `TeachingNotifications.kt:195`
looks risky in isolation but the destination is constructed internally from a validated ID
(`anushtanam://messages/${message.id}`), and `MainActivity.readMessage` re-validates
scheme, host **and** ID on the receiving side (`MainActivity.kt:36-42`). Validated on both
ends.

**FCM payloads are validated before use.** `TeachingNotifications.receive()`
(lines 126-149) checks `kind`, pins `schemaVersion == "1"`, requires both `campaignId` and
`contentId` to pass `TeachingDeliveryPolicy.validId`, rejects already-expired messages,
and deduplicates against a `seen_` marker committed *before* posting so a process crash
cannot replay an alert.

**No SQL injection.** Every interpolated identifier in `ChangedRecordJournal.kt` and
`IdentityRepairMigration.kt` is a hardcoded Kotlin constant — table names, column names,
trigger names. All user-supplied values go through bound `?` parameters. No `@RawQuery`,
no `SimpleSQLiteQuery`, no string-built `WHERE` clauses anywhere in the DAOs.

**No WebView, no `addJavascriptInterface`, no dynamic code loading.** No
`Runtime.getRuntime()`, no `ProcessBuilder`, no reflection-based class loading. No custom
`TrustManager` or `HostnameVerifier` — TLS is left to the platform and to the Firebase SDK.

**No destructive Room fallback.** `MantraSiddhiDatabase.kt:373-377` documents the decision
explicitly: a broken migration must fail loudly rather than silently wipe a user's years
of recorded practice. That is the right availability/integrity trade-off.

**The QR scanner is sandboxed.** `FamilyQrScanner.kt` uses Google Play Services'
`GmsBarcodeScanning`, which runs the camera in the Play Services process — so the app
never holds the `CAMERA` permission at all. The scanned `rawValue` is then validated
against a strict pattern before use (`FamilyQrInvite.tokenFrom`: exact prefix plus
`[a-f0-9]{64}`).

**Release builds are minified and shrunk** (`isMinifyEnabled = true`,
`isShrinkResources = true`), and the `benchmark` variant sets `isDebuggable = false`.
