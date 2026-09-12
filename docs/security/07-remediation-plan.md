# 7. Prioritised Remediation Plan

[← Back to index](README.md)

Ordered by risk reduction per unit of effort, not by severity alone. Effort is a rough
engineering estimate for a developer already familiar with this codebase.

---

## Phase 0 — Do this week

Small changes, disproportionate risk reduction. None requires an architectural decision.

| # | Action | Effort | Closes |
|---|---|---|---|
| 0.1 | **Fix the backup rules.** Invert `message_data_extraction_rules.xml` and `message_backup_rules.xml` to an allowlist; exclude all SharedPreferences (Firebase Auth session) and `sandhya_prefs` (GPS). | 1 h | [M-01](04-android-application.md) |
| 0.2 | **Reject weak PINs** at child enrolment — no repeated digits, no sequential runs, blocklist the ~100 most common 6-digit PINs. | 2 h | mitigates [A-01](01-authentication-and-identity.md) |
| 0.3 | **Cap `rewardDescription`** at 200 chars in `firestore.rules`, truncate in `functions/index.js`, and wrap the `send()` in try/catch. | 1 h | [F-01](03-cloud-functions.md) |
| 0.4 | **Restrict `childCodes` `get`** to `hasRole(request.auth.uid, 'PARENT')`. | 30 m | [R-05](02-firestore-security-rules.md) |
| 0.5 | **Set `exported="false"`** on `CloudSyncJobService`. | 15 m | [M-03](04-android-application.md) |
| 0.6 | **Delete `app-debug.apk` and the 72 stray log files**; move the keystore out of the project tree and tighten its permissions. | 30 m | [S-01](06-supply-chain-and-build.md), [S-04](06-supply-chain-and-build.md) |
| 0.7 | **Verify console configuration**: API key restrictions, email-enumeration protection, Identity Platform abuse prevention, Storage bucket rules, who holds `messageRole`. | 1 h | [A-01](01-authentication-and-identity.md), [R-08](02-firestore-security-rules.md), [F-02](03-cloud-functions.md) |
| 0.8 | **Add `-assumenosideeffects` for `Log.v/d/i`** to `proguard-rules.pro`. | 15 m | [M-05](04-android-application.md) |

**Roughly one day's work. Do 0.1 and 0.7 first** — one is a one-line-per-file change that
stops exporting a child's location and auth session, the other may reveal that several
findings are already partly mitigated in the console.

---

## Phase 1 — Next two weeks

| # | Action | Effort | Closes |
|---|---|---|---|
| 1.1 | **Deploy Firebase App Check** (Play Integrity). Monitoring mode first, then enforce on Firestore, Functions and Auth. `minSdk = 33` means no legacy tail to carry. | 1–2 d | [A-03](01-authentication-and-identity.md) |
| 1.2 | **Stand up CI** — Gradle build + tests, the emulator rules suite as a *required* check, gitleaks, `npm audit`, Dependabot. | 1 d | [S-03](06-supply-chain-and-build.md) |
| 1.3 | **Validate `progress/{uid}` create** with the same schema the update rule implies; remove or harden the delete allowance. | 4 h | [R-02](02-firestore-security-rules.md) |
| 1.4 | **Validate `selfTargets` and `purashcharana`** — `hasOnly` key lists, type checks, bounds, `allow delete: if false`. Reuse the existing helpers at `firestore.rules:163-172`. | 4 h | [R-01](02-firestore-security-rules.md) |
| 1.5 | **Harden `users/{uid}` update** — `hasOnly`, cap `displayName`, validate `photoUrl`, move `fcmToken` to an owner-only subcollection. | 4 h | [R-03](02-firestore-security-rules.md) |
| 1.6 | **Make `linkCode` immutable once the user doc exists**; move Google-child code allocation to an Admin SDK function. | 4 h | [R-04](02-firestore-security-rules.md) |
| 1.7 | **Write the `deleteAccount` callable** and add an in-app Settings entry plus policy links. | 2 d | [P-02](05-data-protection-and-privacy.md) |
| 1.8 | **Tag policy errors** in `message-policy.js` so only intended messages reach the browser. | 2 h | [F-03](03-cloud-functions.md) |

Every rules change in 1.3–1.6 lands with a test in `backend/firebase/tests/`. That is
what 1.2 is for.

---

## Phase 2 — Next quarter

These need design decisions, not just code.

### 2.1 Replace the child credential model — [A-01](01-authentication-and-identity.md), [A-02](01-authentication-and-identity.md)

The core problem: a 6-digit PIN is not a password, and the username is meant to be shared.
Phase 0/1 mitigations (weak-PIN rejection, App Check, enumeration protection) raise the
cost of an attack but do not change that.

Three viable directions, in order of preference:

1. **Parent-provisioned child accounts.** The child profile is created from within a
   verified parent account. The PIN becomes a *local device unlock*, not a network
   credential — so guessing it requires physical possession of the phone, where six digits
   is appropriate. This also solves [P-01](05-data-protection-and-privacy.md) consent in
   the same stroke, and matches how the product is actually used.
2. **Google sign-in as the primary path for children.** Already implemented
   (`completeChildGoogleSetup`), already the more secure route. Make it the default and the
   PIN the fallback.
3. **A server-owned sign-in callable** that enforces per-account lockout and exponential
   backoff, with the direct Identity Toolkit password endpoint disabled for the child
   domain. Most work, keeps the current UX.

Whichever is chosen, **separate the sharing handle from the credential** and make the
handle rotatable. The QR flow already shows the right shape.

### 2.2 Redesign parent–child linking — [A-04](01-authentication-and-identity.md)

Deprecate PIN linking. The QR flow is already built, already consent-based, already
cryptographically sound. If PIN linking must survive, convert it to an approval request:
parent enters the ID only; the child approves on their own device; the PIN never leaves
the child's phone.

### 2.3 Children's-data compliance — [P-01](05-data-protection-and-privacy.md)

Add an age gate and a verifiable parental-consent step (2.1 option 1 delivers this
naturally). Add lawful basis, controller identity, processing region and retention period
to the privacy policy. **Get the DPDP §9 position reviewed by counsel** — this is the one
item in the report where the right next step is legal advice, not engineering. Note that
§9 also restricts behavioural monitoring of children, which bears on the supervision
feature itself, not just on consent.

### 2.4 Retention and encryption — [P-03](05-data-protection-and-privacy.md), [M-02](04-android-application.md)

Set a retention period for `transcription` and `wordScoresJson` (12 months is a reasonable
starting point) after which rows keep scores and timestamps but drop speech-derived text.
Then evaluate SQLCipher for the local database — lower priority once 0.1 has stopped the
database leaving the device.

### 2.5 Operational hardening — [F-02](03-cloud-functions.md), [F-04](03-cloud-functions.md), [R-06](02-firestore-security-rules.md)

Commit an admin-provisioning script so `messageRole` grants are reviewable. Require MFA on
every account holding it. Add per-uid rate limiting to the expensive callables. Set a Cloud
Billing budget alert. **Commission a separate review of the admin website** — it is the
most privileged client in the system and was outside this audit's scope.

---

## What not to do

A few things worth explicitly *not* changing, because they are correct and a well-meaning
cleanup could break them:

- **Do not add a destructive Room migration fallback.** The comment at
  `MantraSiddhiDatabase.kt:373-377` explains why; it is the right call.
- **Do not "simplify" the QR rules.** The `getAfter()` chains in `validQrLink` and the
  `familyInvites` update rule are what make single-use atomic. They look redundant and
  are not.
- **Do not relax `allow delete: if false`** on the practice collections to make account
  deletion easier. Build the Admin SDK path instead (1.7).
- **Do not remove `allow list: if false`** from `childCodes` — that is the control keeping
  bulk enumeration expensive.
- **Do not commit the keystore** "so CI can sign builds." Use CI secrets.

---

## Tracking

| Phase | Findings closed | Residual after phase |
|---|---|---|
| 0 | M-01, M-03, M-05, S-04, R-05, F-01, part of S-01 | 1 critical, 3 high, 10 medium, 7 low |
| 1 | A-03, R-01, R-02, R-03, R-04, F-03, P-02, S-03 | 1 critical, 2 high, 3 medium, 7 low |
| 2 | A-01, A-02, A-04, A-05, P-01, P-03, M-02, F-02, F-04, R-06, S-02 | low only |

The critical finding closes in Phase 2 because it requires a design change. Phases 0 and 1
substantially raise the cost of exploiting it in the meantime — but they do not close it,
and the plan should not be read as if they do.
