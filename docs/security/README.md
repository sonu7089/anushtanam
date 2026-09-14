# Security Audit — Anushtanam (`shastralife.anushtanam`)

**Audit date:** 2026-09-12
**Revision audited:** `master` @ `df5f40f` (plus uncommitted working-tree changes)
**Method:** static review of source, configuration and security rules
**Scope:** Android client (`frontend/android`, `core/data`, `core/domain`), Firestore
security rules, Cloud Functions, build and signing configuration.

---

## What this application is, from a security point of view

Anushtanam is a Vedic mantra-recitation app with a **family supervision feature**: an
adult ("parent") account links to a **child account**, assigns recitation goals with
real-world rewards, and reads the child's practice statistics. Children get their own
credentials and sign in on their own device.

That single fact drives the severity ratings in this report. This is not a note-taking
app. It is a **child-facing application that authenticates minors, records their voice,
stores their transcriptions and daily activity timeline, and exposes part of that to a
second party.** Security defects here are child-safety defects.

The backend is serverless: the Android client talks directly to Firestore, and the
security rules *are* the authorization layer. There is no application server in the
request path, so a rules gap is a direct, unmediated data breach.

---

> Authentication remediation is implemented in source; see the [2026-09-13 review and rollout status](01-implementation.md). The table below describes the original audit, not current closure status.

> Firestore rules remediation has also been reviewed and implemented in source; see [corrections, verification and deployment requirements](02-implementation.md).

> Cloud Functions remediation is implemented in source; see [audit corrections, tests and remaining rollout work](03-implementation.md).

> Android hardening is implemented in source; see [review corrections, backup policy and remaining device verification](04-implementation.md). Application-level database encryption remains a separate decision.

## Findings at a glance

For current priorities, use the [reviewed remediation plan](07-remediation-plan.md)
(14 September 2026). It reconciles the original actions with implementation records,
preserves later product decisions and separates source changes from deployment and
operational verification. The findings below are historical.

| # | Finding | Severity | Report |
|---|---------|----------|--------|
| A-01 | Child accounts are protected by a 6-digit PIN with no lockout or weak-PIN rejection | **Critical** | [Auth](01-authentication-and-identity.md) |
| A-02 | The child's shareable "ID" is also their auth username, making credentials half-public by design | **High** | [Auth](01-authentication-and-identity.md) |
| A-03 | Firebase App Check is not deployed; the backend accepts requests from any client | **High** | [Auth](01-authentication-and-identity.md) |
| A-04 | Parent-side PIN linking hands the parent full child credentials and an in-app brute-force oracle | **Medium** | [Auth](01-authentication-and-identity.md) |
| A-05 | No re-authentication for sensitive operations | **Low** | [Auth](01-authentication-and-identity.md) |
| R-01 | `selfTargets` and `purashcharana` accept arbitrary unvalidated documents | **Medium** | [Rules](02-firestore-security-rules.md) |
| R-02 | `progress/{uid}` create is entirely unvalidated and is readable by the linked parent | **Medium** | [Rules](02-firestore-security-rules.md) |
| R-03 | `users/{uid}` update validates only two fields; `displayName`, `photoUrl`, `fcmToken` are free-form | **Medium** | [Rules](02-firestore-security-rules.md) |
| R-04 | A user with no `linkCode` can self-assign an arbitrary unclaimed one | **Medium** | [Rules](02-firestore-security-rules.md) |
| R-05 | `childCodes/{code}` is readable by any signed-in user, enabling code→uid resolution and unsolicited link requests | **Medium** | [Rules](02-firestore-security-rules.md) |
| R-06 | `publishedContent` is world-readable with no rate limit (billing exposure) | **Low** | [Rules](02-firestore-security-rules.md) |
| R-07 | Rules depend on 3–7 `get()`/`getAfter()` lookups per write, approaching hard limits | **Low** | [Rules](02-firestore-security-rules.md) |
| R-08 | No Cloud Storage rules in the repository despite a provisioned bucket | **Low** | [Rules](02-firestore-security-rules.md) |
| F-01 | `onGoalCreated` embeds a 200 KB-capable parent-controlled string into a push notification | **Medium** | [Functions](03-cloud-functions.md) |
| F-02 | The admin callable API is authorized by a custom claim with no provisioning path in the repo | **Medium** | [Functions](03-cloud-functions.md) |
| F-03 | Internal error messages are returned to the browser | **Low** | [Functions](03-cloud-functions.md) |
| F-04 | No rate limiting on expensive callable operations | **Low** | [Functions](03-cloud-functions.md) |
| F-05 | FCM tokens can be registered by a user who does not own them | **Low** | [Functions](03-cloud-functions.md) |
| M-01 | `allowBackup="true"` exports the unencrypted practice database, location and auth state | **High** | [Android](04-android-application.md) |
| M-02 | The Room database holds transcriptions and history in plaintext SQLite | **Medium** | [Android](04-android-application.md) |
| M-03 | `CloudSyncJobService` is exported without need | **Low** | [Android](04-android-application.md) |
| M-04 | Debug variant enables cleartext traffic and an exported review activity | **Low** | [Android](04-android-application.md) |
| M-05 | Release builds retain `Log.d`/`Log.w` calls | **Low** | [Android](04-android-application.md) |
| P-01 | No documented lawful basis, consent flow, or age gate for processing children's data | **High** | [Privacy](05-data-protection-and-privacy.md) |
| P-02 | No account deletion or data erasure path in the client | **Medium** | [Privacy](05-data-protection-and-privacy.md) |
| P-03 | Cloud-side practice history has no retention limit | **Low** | [Privacy](05-data-protection-and-privacy.md) |
| S-01 | Release signing key material sits unencrypted in the working tree | **Medium** | [Supply chain](06-supply-chain-and-build.md) |
| S-02 | Dependencies are 12–24 months stale with no update or scanning process | **Medium** | [Supply chain](06-supply-chain-and-build.md) |
| S-03 | No CI: rules tests, dependency audit and secret scanning are manual | **Medium** | [Supply chain](06-supply-chain-and-build.md) |
| S-04 | A 67 MB debug APK and 60+ build logs sit in the working tree | **Low** | [Supply chain](06-supply-chain-and-build.md) |

**Totals:** 1 critical, 4 high, 12 medium, 12 low.

---

## Credit where it is due

The audit found a codebase that has clearly been thought about. Worth recording, because
it shapes the remediation advice:

- **The Firestore rules are unusually rigorous.** Most of the write paths carry full
  schema validation (`validEntry`, `validRecord`, `validJapaGoal`, `validAssignedGoal`),
  field-level diff restrictions via `affectedKeys().hasOnly(...)`, and cross-document
  consistency checks with `getAfter()`. The `goals` collection correctly splits parent
  edit rights from the child's two-field progress update. This is well above the norm.
- **The QR family-link flow is well designed.** A 256-bit `SecureRandom` token, a
  5-minute lifetime, single-use enforced atomically in the rules via `getAfter()`, and
  `allow delete: if false` so a consumed invite cannot be recreated to replay the secret.
- **There is a real rules test suite** (`backend/firebase/tests/`, nine files including
  `child-auth.test.cjs` and `family-rules.test.cjs`) that runs against the emulator.
- **No SQL injection.** Every interpolated identifier in the Room migrations and the
  trigger journal is a hardcoded constant; all user values go through bound parameters.
- **No WebView anywhere**, so the usual Android JavaScript-bridge class of bug is absent.
- **`SecureRandom` is used** for child codes and invite tokens, not `java.util.Random`.
- **Signing keys are correctly gitignored** and are not in the committed history.
- **Notification intents are explicit** (`setClassName`) and `FLAG_IMMUTABLE`, so there
  is no intent-redirection or PendingIntent-hijack surface.
- **No destructive Room migration fallback**, with a deliberate comment explaining why.

The gaps in this report are concentrated in two places: **the child credential model**,
and **what happens at the edges of the otherwise-strong rules** — the handful of
collections that were left open, and the platform-level configuration (App Check,
backup) that lives outside the rules file.

---

## The one thing to fix first

**A-01.** A child's account is protected by a 6-digit PIN — one million possibilities —
with no lockout, no weak-PIN rejection, and no attestation on the endpoint that checks
it. The username is a number the child is *encouraged to share*. Everything else in this
report is secondary to that.

---

## Reports

1. [Authentication and identity](01-authentication-and-identity.md)
2. [Firestore security rules](02-firestore-security-rules.md)
3. [Cloud Functions](03-cloud-functions.md)
4. [Android application](04-android-application.md)
5. [Data protection and privacy](05-data-protection-and-privacy.md)
6. [Supply chain and build integrity](06-supply-chain-and-build.md)
7. [Prioritised remediation plan](07-remediation-plan.md)

---

## Method and limits

This was a **static review**. Specifically:

- Firestore rules were read line by line against the client code that exercises them.
- Cloud Functions were read for authorization, input validation and injection.
- The Android manifest, Gradle configuration, ProGuard rules, storage layer and
  notification/intent surfaces were reviewed.
- Dependency versions were compared against their release timelines.

**Not performed** — and worth commissioning before a wider release:

- **Live testing against the deployed Firebase project** (`anushtanam-shastralife`).
  Several findings depend on **console-side** configuration this repository cannot show:
  API key restrictions, Identity Platform abuse protection, App Check enforcement, IAM
  roles, and who holds the `messageRole` custom claim. Each is flagged where relevant.
  **Where this report says a control is missing, it means "absent from the repository" —
  confirm against the console before closing any such finding.**
- Dynamic analysis: runtime instrumentation, traffic interception, or a decompile of the
  shipped APK.
- Review of the **admin website** that calls `adminMessages`. It is not in this
  repository, and it is the most privileged client in the system.
- `npm audit` / CVE lookup against the pinned lockfiles (requires network access).

Severity blends exploitability and impact, weighted for the fact that the affected users
are minors.
