# Supply chain and build integrity implementation

Reviewed 2026-09-14 against [the original audit](06-supply-chain-and-build.md).

## Review decisions

The missing CI and dependency monitoring findings apply. AGP is already 8.13.2,
not the audit's 8.10.1. Dependency age is a review signal, not evidence of an
exploitable vulnerability. Existing compatibility pins are preserved.

The keystore and encrypted credentials exist locally (see migration progress below). The audit's Unix
`world-readable` claim cannot establish Windows ACL exposure. An upload key also
does not sign updates to Play-distributed installations when Play uses a different
app-signing key. It remains sensitive and should be kept outside this checkout.
`local.properties` was checked by property name only and contains only `sdk.dir`.

## Implemented controls

- `security.yml` runs on pushes, PRs, merge queues, manual dispatch and weekly:
  release assembly, artwork integrity, app/domain/data unit tests, release manifest
  and backup policy tests, all Firebase emulator tests, performance rules tests,
  production npm audit and secret scanning. It uses read-only repository permissions,
  immutable action references, lockfile installs and no production signing secrets.
  It does not publish or deploy anything. Device instrumentation remains a separate
  release check; this workflow does not run a device emulator.
- Dependabot checks Gradle, both npm manifests and Actions weekly. Version groups
  explicitly apply only to version updates; security updates remain separate.
  No blanket ignores suppress security fixes and nothing auto-merges. Maintainers
  must preserve/revalidate the documented compatibility constraints.
- Quarterly runs on March/June/September/December 14 produce a maintainer checklist
  alongside the automated checks. Next review: 2026-12-14. Review Firebase, Coil
  and Google sign-in libraries. Recheck Kotlin/KSP/Compose and DataStore native alignment;
  update the catalog dates after completing the human review.
- Gradle verifies the distribution against the official Gradle 8.13 SHA-256.
- Release signing reads only environment variables and requires a keystore outside
  the canonical project path. Partial configuration fails instead of silently
  falling back. Builds with no signing configuration warn and use the debug key.
- `.gitleaksignore` suppresses only the reviewed fingerprint of the public Firebase
  client API key in the historical `app/google-services.json`. Other findings remain
  enabled, including other findings in that file. The full-history scan is redacted.
- `build-logs/` is ignored. Use it for command output, for example
  `./gradlew.bat :app:assembleRelease *> build-logs/release.log` in PowerShell.
  Firebase itself still creates its diagnostic logs in its working directory.
  Archived 44 existing ignored logs from the root and backend folders under
  `build-logs/archive-2026-09-14/`, preserving them for inspection. The audit's root
  `app-debug.apk` no longer exists; the existing release APK was retained.
- Fixed an existing generated-privacy-assets task dependency found by the combined
  CI build: both asset merging and release lint now depend on document generation.

## Release signing migration (required before publishing)

The old `keystore.properties` is deliberately no longer read. Preserve the existing
upload key; do not generate a replacement merely to migrate its storage.

1. Move the existing keystore to an owner-controlled folder outside the checkout,
   Downloads, cloud sync and routine unencrypted backups. Choose the destination
   according to this workstation's backup/sync configuration. Keep a secure recovery
   copy. On Windows, restrict its ACL to the owner and necessary system access;
   `chmod 600` is not a Windows ACL control.
2. Store the passwords in your OS vault or secret manager. After verifying the vault
   entries and key access, remove the old plaintext properties file and any insecure
   copies. No key/password contents were printed during this implementation.
3. Inject `ANDROID_KEYSTORE_PATH` (absolute external path),
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` into
   the build process. Avoid literal passwords in shell history or repository files.
4. Set `ANDROID_REQUIRE_RELEASE_SIGNING=true` for publishing, then run
   `./gradlew.bat --no-daemon --no-configuration-cache :app:bundleRelease`.
   Clear the process environment afterward. Keep configuration caching disabled
   for builds using real credentials.
5. Verify Play App Signing and the upload certificate in Play Console. Verify the
   Firebase Android API key restrictions against package and certificate identity.

Migration progress (2026-09-14): moved the existing keystore from the checkout to
`C:\Users\PC\AppData\Local\Anushtanam\signing\upload-keystore.jks` and verified its
SHA-256 was unchanged. The signing folder, keystore and encrypted credentials have
protected Windows ACLs allowing only the original owner and SYSTEM.
The original in-project keystore is gone. Migrated the passwords to
`C:\Users\PC\AppData\Local\Anushtanam\signing\signing-credentials.clixml`, using
Windows DPAPI-encrypted SecureStrings for the keystore owner's account. Verified
decryption round-trip and that both passwords unlock the existing private key,
then removed the old plaintext `keystore.properties`.

`scripts/Invoke-SignedRelease.ps1` loads these credentials, verifies them using the
JDK helper, and supports `-Task Validate`, `-Task Bundle` and `-Task Apk`. It passes
passwords only through the process environment, restores prior environment values
in `finally`, and disables Gradle configuration caching and daemon reuse. The README
documents paths and commands. This step did not publish an artifact or rotate the key.

[Windows DPAPI credential files](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/import-clixml)
are bound to the Windows user and computer. A separate secure recovery copy of the
keystore and passwords is still needed before replacing the machine/profile. Backup
configuration is not verified. Deleting the plaintext file does not erase historical
backups or guarantee forensic erasure of old disk blocks.

Portable backup tooling is prepared in `scripts/Manage-SigningBackup.ps1` with
checksum-pinned age 1.3.2. It encrypts the key and credentials in memory, validates
the encrypted round-trip, and supports restoring to new Windows DPAPI credentials.
The creation/restore workflow passed using a disposable signing key. The actual
backup was subsequently created in a separate interactive PowerShell window, with
the owner entering the recovery passphrase privately. Its final filename is produced
only after the encrypted round-trip check succeeds. Confirmed the final `.age` header,
4,134-byte size, and SHA-256
`A0B00EE61FDF55BADFCC1037B25D3E4A52ECD4CBC866F67F288B4A63DB350FB9`.
The owner confirmed Google Drive upload and explicitly skipped verification of a
downloaded copy. The remote copy has not been independently verified.
See the README for commands and recovery steps.

Play Console screenshots supplied by the owner show the app signing key marked
"In use" and a registered upload certificate. Read the public SHA-256 from the
local keystore and confirmed an exact match with the screenshot:
`A2:56:B7:26:45:3C:B7:77:15:A1:41:0B:8B:C2:CE:DD:01:B5:FC:D5:6C:2A:D4:E4:35:61:42:1C:5B:A3:70:A0`.
No signing change/reset is required for this migration. API key restrictions and
Firebase registration of the Play app-signing certificate remain separately unverified.

## GitHub activation

The owner enabled the dependency graph, Dependabot alerts and Dependabot security
updates in repository settings, confirmed by screenshots. Actions API checks confirm
workflows are enabled, default token permissions are read-only, and workflow-created
PR approvals are disabled. The local files are being published to activate CI and
scheduled version updates. After the first successful run, require these checks in
the protected branch/ruleset, with bypass restricted:

- `Firestore rules and performance`
- `Android build and unit tests`
- `Functions production audit`
- `Secret scan`

Deploy only a reviewed commit whose checks passed. This repository has no automatic
deployment pipeline; branch protection cannot prevent a manual Firebase CLI deploy.
GitHub authentication works in the normal Windows account context; the earlier
authentication failures occurred in the restricted execution environment.
If transferring to a GitHub organization, configure the Gitleaks action license as
required by its upstream terms.

## Production dependency audit

`npm audit --omit=dev --json --prefix backend/firebase/functions` on 2026-09-14
reported **14 moderate, 0 high, 0 critical** dependency findings. These include
transitive effects, not 14 distinct advisories. Root advisories:

- [qs comma parsing limit bypass](https://github.com/advisories/GHSA-x5fp-wj9c-mxmx)
- [qs attacker-controlled isBuffer denial of service](https://github.com/advisories/GHSA-4mjr-xmp4-gh2g)
- [uuid buffer bounds check](https://github.com/advisories/GHSA-w5hq-g745-h8pq)

The report marks some affected paths as having no automatic fix. No forced major
upgrades or transitive overrides were applied. CI prints all findings and fails on
high/critical findings or audit service errors (`--audit-level=high`); moderate
findings remain open for dependency triage. This is not a clean vulnerability audit.
The full local report is `build-logs/security-06-npm-audit.json`.

## Validation

- JDK 17 combined Gradle run: release assembly, artwork verification, release lint,
  8 manifest policy cases and backup allowlist/encryption checks passed. The release
  APK produced by this validation uses the debug certificate and is not publishable.
- Kotlin unit tests: 367 domain, 144 data and 123 app tests passed (634 total).
- Firebase emulator suite: 74 tests passed.
- Rules/performance suite: 37 tests passed.
- Gitleaks 8.30.1: 102 commits scanned, no remaining findings after the exact public
  Firebase identifier exception above.
- Actionlint 1.7.12 and YAML parsing validate the workflow/configuration.
- Signing configuration rejects missing required credentials, partial credentials
  and an in-project keystore; the existing legacy properties are not consumed.
- Windows credential migration: DPAPI round-trip and real private-key unlock passed;
  incorrect store/key passwords were rejected. The release helper restores absent
  environment variables as absent after both success and failure. Protected file
  ACLs, two encrypted SecureString entries and removal of plaintext were verified.

See local `build-logs/security-06-*` for detailed run output. GitHub-hosted execution,
real upload-key signing, Play settings and device instrumentation are not verified
by these local checks.

Configuration references: [Dependabot options](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference),
[Gitleaks action](https://github.com/gitleaks/gitleaks-action),
[Gradle distribution checksum](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256).
