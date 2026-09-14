# Anushtanam

A native Android app for Vedic mantra practice, japa, sankalpa, family progress, and Sandhya reminders. It ships with the Gayatri; the mantras it can teach are JSON definitions, not code — see [adding a mantra](docs/adding-a-mantra.md).

## Where to work

| Directory | Responsibility |
| --- | --- |
| `frontend/android` | Android application, Compose screens, UI state, ViewModels, navigation, theme, artwork, and fonts. Gradle name remains `:app`. |
| `core/domain` | Pure Kotlin models, repository contracts, use cases, mantra evaluation, the mantra catalog (`src/main/resources/mantras`), and solar calculations. No Android, Firebase, or UI dependencies. |
| `core/data` | Room, DataStore, Firebase clients, audio, speech recognition, notifications, reminder scheduling, and dependency injection. |
| `core/resources` | Shared strings, notification artwork, reference audio, and benchmark timestamps. |
| `backend/firebase` | Deployed Firestore rules and the optional notification Cloud Function. |
| `docs` | Architecture, design decisions, asset provenance, licenses, and UI review screenshots. |

The frontend calls existing use cases and data services. The domain defines contracts; the data module implements them. The Firebase backend is deployed independently. Local evaluation and offline storage still run on the device.

## Adding a mantra

Add `core/domain/src/main/resources/mantras/<id>.json`, list it in `catalog.json`, and you are done — no Kotlin changes. Point `defaultMantraId` at it to make it the mantra the app teaches. The full field-by-field guide, including optional reference audio and dictation quirks, is in [docs/adding-a-mantra.md](docs/adding-a-mantra.md).

## Build and check

Use JDK 17 or a compatible newer JDK, Android SDK 36, and the included Gradle wrapper. Set `sdk.dir` in your untracked `local.properties` as usual.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest
```

The debug APK is `frontend/android/build/outputs/apk/debug/app-debug.apk`. The application ID and database name are unchanged. The Room schema is at v17. The v14 to v15 migration repairs legacy identity/association collisions before adding uniqueness constraints and preserves original repair snapshots. The v16 to v17 migration adds durable changed-record work and measured query indexes; see [step 08 implementation and verification](doc/perf/step-08-changed-records.md). Saved assessments are immutable; restore supports either association order. See [identity repair and verification](doc/perf/step-03-identities.md) and [cloud validation and rollout](doc/perf/step-02-validation.md). Release signing uses process environment variables and an external keystore; see [signing setup and build security](docs/security/06-implementation.md).

MF-07 adds Google sign-in, account linking and profile photos. The refreshed Firebase configuration includes the Google OAuth client. See [MF-07 setup and verification](docs/mentor-feedback/mf-07-implementation.md) for the remaining live account checks.

Children can also use their own Google account. Parents can connect by scanning a child's QR code or entering their ID and PIN. See [child sign-in and family linking setup](docs/mentor-feedback/child-google-family-linking.md), including the required Firestore rules update.

## Release signing on this Windows workstation

The existing Play upload key is stored outside the repository:

- **Keystore:** `C:\Users\PC\AppData\Local\Anushtanam\signing\upload-keystore.jks`
- **Encrypted passwords:** `C:\Users\PC\AppData\Local\Anushtanam\signing\signing-credentials.clixml`
- Portable location: `%LOCALAPPDATA%\Anushtanam\signing\`.

The passwords use Windows DPAPI encryption for the Windows account that saved them
on this computer. The folder and files are restricted to that account and SYSTEM.
Do not commit or share these files. The encrypted credential file alone is not a
portable recovery backup; retain the key and passwords in a separate secure recovery
location before replacing this computer or Windows profile.

Run from PowerShell with JDK 17 configured in `JAVA_HOME`, or pass `-JavaHome`:

```powershell
# Check that both passwords unlock the existing private key; produces no build.
.\scripts\Invoke-SignedRelease.ps1 -Task Validate
# Build a signed Play bundle (does not upload it).
.\scripts\Invoke-SignedRelease.ps1 -Task Bundle
# Build a signed release APK.
.\scripts\Invoke-SignedRelease.ps1 -Task Apk
```

The helper decrypts passwords into the process environment only, requires real
release signing, disables Gradle daemon reuse/configuration caching, and restores
the prior environment afterward. It does not print passwords or pass them on the
command line. The old plaintext `keystore.properties` is no longer used. For CI,
inject the signing environment variables from the CI secret manager instead of
copying the Windows credential file. See [signing setup and build security](docs/security/06-implementation.md).

### Portable recovery backup for Google Drive

Run this in an interactive **PowerShell 7** terminal on the current Windows account:

```powershell
.\scripts\Manage-SigningBackup.ps1 -JavaHome 'C:\Users\PC\.jdks\corretto-17.0.13'
```

If a terminal cannot find/access the credentials, open Windows PowerShell from the
Start menu as `PC` and launch the installed PowerShell 7 explicitly:

```powershell
& 'C:\Users\PC\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File 'C:\Users\PC\Downloads\mob_ga1\scripts\Manage-SigningBackup.ps1' -JavaHome 'C:\Users\PC\.jdks\corretto-17.0.13' -SigningDirectory 'C:\Users\PC\AppData\Local\Anushtanam\signing'
```

The credentials require the original Windows account; running under another account
or a restricted terminal does not grant access merely because the working folder is
correct. Do not change the signing folder permissions to make it broadly readable.

The helper uses checksum-pinned [age](https://github.com/FiloSottile/age) encryption.
Enter a new, strong recovery passphrase locally and keep it outside Google Drive
(for example, in your password manager). It encrypts the keystore, alias and both
signing passwords, then asks for the passphrase again to verify recovery. Plaintext
signing passwords are never written to a temporary file. Only after verification
does it produce `build-logs/backups/anushtanam-signing-backup.age` for upload to Drive.
Do not upload the raw keystore, decrypted JSON or a recovery passphrase alongside it.

After downloading the encrypted backup from Drive, verify it using:

```powershell
.\scripts\Manage-SigningBackup.ps1 -Action Verify -Path 'C:\path\to\anushtanam-signing-backup.age'
```

To restore on a replacement Windows machine/account, install PowerShell 7 and JDK 17,
retrieve this repository, and run:

```powershell
.\scripts\Manage-SigningBackup.ps1 -Action Restore -Path 'C:\path\to\anushtanam-signing-backup.age'
```

Restore checks the key hash and both signing passwords, applies restricted Windows
permissions, and encrypts the passwords for the new Windows account. It refuses to
overwrite existing signing files; use `-SigningDirectory` to select a different empty
folder. The `.age` format is also decryptable with standard age on other platforms;
the decrypted JSON contains `KeyStoreBase64`, `KeyAlias`, `StorePassword` and
`KeyPassword`. Keep decrypted contents private. New backups require a new `-Path`
when an existing verified backup is already present.

Backup status (2026-09-14): the real encrypted backup was created and passed the
local recovery check. The owner entered the recovery passphrase privately. The file
is 4,134 bytes, with SHA-256
`A0B00EE61FDF55BADFCC1037B25D3E4A52ECD4CBC866F67F288B4A63DB350FB9`.
The owner confirmed Google Drive upload and chose to skip the downloaded-copy
checksum check. The remote copy has not been independently verified.

Play Console verification (2026-09-14): the supplied screenshots show the app signing
key in use. The registered upload certificate SHA-256 exactly matches the local key:
`A2:56:B7:26:45:3C:B7:77:15:A1:41:0B:8B:C2:CE:DD:01:B5:FC:D5:6C:2A:D4:E4:35:61:42:1C:5B:A3:70:A0`.
No Play signing change or upload-key reset is needed for this storage migration.

## UI review

The debug build includes an offline review activity using the production components and sample data. It is absent from release builds and has no launcher entry. Open a page on an emulator:

```powershell
adb shell am start -n shastralife.anushtanam/com.mantrasiddhi.review.DesignReviewActivity --es page home --ez dark false
```

Pages: `welcome`, `parent-auth`, `child-auth`, `child-id`, `home`, `settings`, `progress`, `practice`, `recitation`, `targets`, `set-target`, `family`, `children`, `sandhya`, and `purashcharana`, `voice`, and `empty`. Authentication pages expose the existing forms; use them only for visual inspection unless you intend to sign in. Review home and settings use local fixtures.

See [adding a mantra](docs/adding-a-mantra.md), [the screenshot gallery](docs/ui-review/README.md), [verification results](docs/verification.md), [architecture](docs/architecture.md), [design decisions](docs/design-decisions.md), and [artwork prompts and provenance](docs/artwork.md).
