# 6. Supply Chain and Build Integrity

[← Back to index](README.md)

**Files reviewed:** `gradle/libs.versions.toml`, `frontend/android/build.gradle.kts`,
`backend/firebase/functions/package.json`, `.gitignore`, `keystore.properties.template`,
and the working-tree state.

## Overall assessment

Secret hygiene in **version control** is correct — nothing sensitive has ever been
committed, which was verified against the full history, not just the current tree. The
weaknesses are in the **working environment** and in **process**: there is no CI, so every
safeguard the repository contains is run manually or not at all.

---

## S-01 — Release signing key material sits unencrypted in the working tree · **MEDIUM**

```
-rw-r--r-- keystore.properties      130 bytes   (store/key passwords, alias, path)
-rw-r--r-- upload-keystore.jks     2728 bytes   (the Play upload key)
```

Both are correctly excluded by `.gitignore`:

```
# Signing files
*.jks
*.keystore
*.pepk
keystore.properties
```

and a history scan across all refs confirms **neither has ever been committed**. A
`keystore.properties.template` is committed instead, which is the right pattern. Credit
where due — this is the part most projects get wrong.

The residual risk is that the **private key and the passwords that unlock it sit
side-by-side, unencrypted, in a developer's `Downloads` folder** on a workstation, with
world-readable file permissions. `build.gradle.kts:63-70` reads them directly at
configuration time. Anything that reads that directory — malware, a backup client, a
sync tool, a shared machine, an inadvertently-shared archive — gets a key that can sign
an APK Android will accept as an authentic update to this app.

Play App Signing limits the blast radius (an upload key can be rotated by Google, unlike a
legacy app-signing key), which is why this is Medium rather than High. It still means an
attacker can publish to the developer's Play account if they also hold the session, and
can sign convincing sideload builds regardless.

**Remediation.**
1. Move the keystore out of the project tree entirely — a path outside any synced or
   backed-up folder — and point `storeFile` at it via an absolute path or an environment
   variable.
2. Tighten file permissions to owner-only.
3. Keep the passwords in a secret manager or the OS keychain rather than a plaintext
   `.properties` file; read them from the environment in `build.gradle.kts`.
4. Confirm Play App Signing is enabled so the upload key can be rotated if it leaks.
5. Verify `local.properties` (present, gitignored, 53 bytes) holds only the SDK path.

---

## S-02 — Dependencies are stale with no update process · **MEDIUM**

Against a 2026-09-12 audit date, the direct dependencies are 1–3 years behind:

| Dependency | Pinned | Approx. age |
|---|---|---|
| Kotlin | 1.9.24 | ~2 years 4 months (Kotlin 2.x is current) |
| Room | 2.6.1 | ~2 years 10 months |
| Compose BOM | 2024.06.00 | ~2 years 3 months |
| Compose Material3 | 1.2.1 | ~2 years 6 months |
| navigation-compose | 2.7.7 | ~2 years 7 months |
| Coroutines | 1.8.1 | ~2 years 5 months |
| Hilt | 2.51.1 | ~2 years 5 months |
| Coil | 2.6.0 | ~2 years 6 months |
| Lifecycle | 2.8.3 | ~2 years 3 months |
| Firebase BoM | 33.5.1 | ~1 year 10 months |
| androidx.credentials | 1.3.0 | ~2 years |
| firebase-admin (Functions) | ^12.6.0 | v13 available |

This is a **conservative** posture rather than a neglected one, and the file shows it. The
catalog carries genuine engineering reasons for specific pins:

- `datastore = "1.1.7"` with "Do NOT bump to 1.2.0 — it regressed 16 KB ELF alignment
  (flutter/flutter#182898)". That is a real, correctly-diagnosed constraint.
- `agp = "8.10.1"` — "Deliberately NOT AGP 9.x — that would force a Kotlin 2 migration
  (behavior risk) for an advisory-only console metric."
- Espresso and ext-junit were recently bumped for an Android 17 compatibility fix.

So someone is paying attention. The problem is that the **only** trigger for an update is a
functional break. Nothing in the repository watches for a security advisory, and there is
no scheduled review. Coil in particular is an image decoder — historically a
vulnerability-prone class of dependency — and Firebase Auth being 22 months behind matters
for an app whose central weakness is an authentication flow.

Note also that `backend/firebase/functions/package.json` uses caret ranges (`^12.6.0`,
`^6.1.0`). A `package-lock.json` is committed, which pins the resolved tree for
reproducibility — good — but the caret means a lockfile regeneration silently pulls a
different minor version.

**Remediation.**
1. Enable **GitHub Dependabot** (or Renovate) for Gradle and npm, with security updates
   separated from version updates so advisories can be taken without a feature bump.
2. Run `npm audit --production` against `backend/firebase/functions` and record the
   result; it requires network access and was not possible in this review.
3. Schedule a quarterly dependency review. Prioritise Firebase, Coil and the credentials
   library — the three with the most direct security surface.
4. Keep the documented pins; annotate each with a review date so a constraint does not
   outlive its reason.

---

## S-03 — No CI, so every safeguard is manual · **MEDIUM**

There is no `.github/workflows/`, no `.gitlab-ci.yml`, no CI configuration of any kind.

This is the finding with the widest downstream effect, because the repository already
**contains** good safeguards that nothing runs automatically:

- **Nine Firestore rules test files** in `backend/firebase/tests/` — `child-auth`,
  `family-rules`, `messages-rules`, `messages-policy`, `messages-workflow`,
  `backup-rules`, `backup-validation`, `backup-fixtures`, `session-counting` — wired to
  `npm test` against the emulator. Security rules are the entire authorization layer for
  this product (see [Rules](02-firestore-security-rules.md)); their tests should gate
  every merge and every deploy. Today they run when someone remembers.
- **A rules-performance audit** (`doc/perf/database-rules-audit.cjs`, invoked by
  `npm run test:perf`) that is exactly the right tool for [R-07](02-firestore-security-rules.md).
- **An artwork-integrity check** (`verifyArtwork` in `frontend/android/build.gradle.kts`)
  that SHA-256-verifies every generated asset against a manifest and fails the build on a
  mismatch. A genuinely good supply-chain control for assets — and it is wired into
  `preBuild`, so this one *does* run.
- **Kotlin unit and instrumentation tests** across `core/domain`, `core/data` and
  `frontend/android`.

Nothing runs secret scanning either. Secret hygiene is currently one developer
remembering what `.gitignore` covers — which has worked so far, verified across the whole
history, but is not a control.

**Remediation.** A single workflow closes most of this:

```yaml
on: [push, pull_request]
jobs:
  verify:
    steps:
      - Gradle: assembleRelease + test + verifyArtwork
      - Firestore rules: npm test (emulator)
      - Dependabot alerts enabled at the repo level
      - gitleaks / trufflehog secret scan
      - npm audit --production on backend/firebase/functions
```

Make the rules tests a **required** status check. Of everything in this report, that is
the single highest-leverage process change: it turns the rules from a file someone edits
into a file that cannot regress silently.

---

## S-04 — Build artifacts and logs accumulate in the working tree · **LOW**

```
app-debug.apk                    67 MB
*.log (root + backend/firebase)  72 files, ~1.5 MB
firebase-debug.log, firestore-debug.log
```

All are gitignored and none are committed. The logs were scanned for credential material —
API keys, FCM tokens, refresh tokens, bearer tokens, private keys, email addresses — and
**nothing sensitive was found**. The single pattern hit was the word `fcmToken` inside a
copy of the rules file. So this is hygiene, not exposure.

Two things still make it worth clearing:

- The 67 MB `app-debug.apk` is a **debuggable build with cleartext traffic enabled and an
  exported `DesignReviewActivity`** (see [M-04](04-android-application.md)). A debuggable
  APK allows full runtime inspection of the app's data. If it is ever shared, attached to
  a bug report, or sideloaded onto a real user's device, those properties are live.
- 72 log files from `step09-*`, `step10-*`, `step10b-*` and `mf10-*` runs are a
  build-output directory that has escaped into the source tree. They make it harder to
  notice when something *is* sensitive.

**Remediation.** Delete the APK and the logs. Redirect future build output to a single
gitignored `build-logs/` directory so the root stays readable. Consider adding
`*-debug.apk` and `step*.log` to `.gitignore` explicitly.

---

## Reviewed and found sound

**Nothing sensitive is in git history.** Verified with `git log --all --name-only` across
every ref for `keystore.properties`, `*.jks` and `*.apk` — zero hits. The `.gitignore`
covers signing files, `local.properties`, all build artifacts, `node_modules/` and logs.
A `keystore.properties.template` documents the required keys without the values.

**`google-services.json` being committed is correct.** It contains a Firebase API key,
which is a public project identifier, not a credential — it is embedded in every shipped
APK and is extractable by anyone. Committing it is standard and expected. The relevant
control is not secrecy but **API key restriction** in the Google Cloud console (restrict
the Android key to the app's package name and signing certificate fingerprint) plus
**App Check** — see [A-03](01-authentication-and-identity.md). Verify the key restriction
in the console; it cannot be confirmed from the repository.

**Release builds are minified and shrunk**, with `debugSymbolLevel = "FULL"` so Play can
symbolicate crashes without shipping symbols to users.

**The release signing fallback is safe and loud.** When `keystore.properties` is absent,
the build falls back to the debug key and emits a warning that the result is not
publishable (`build.gradle.kts:82-91`). Play rejects debug certificates, so a
misconfigured CI build fails at upload rather than shipping something wrong.

**`verifyArtwork` is a real integrity control** — it SHA-256s every artwork master against
a committed manifest, verifies the manifest and generator script hashes against a
`generated.json`, checks every derived `.webp` matches its recorded hash, and asserts the
resource set exactly equals the generated set. A modified asset fails the build. This is
more supply-chain rigour than most apps apply to code, let alone images.

**`node` engine is pinned to 22** in the Functions package, a currently-supported LTS line.

**A `package-lock.json` is committed** for both `backend/firebase` and
`backend/firebase/functions`, so the dependency tree is reproducible.
