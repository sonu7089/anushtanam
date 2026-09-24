# 5. Data Protection and Privacy

[← Back to index](README.md)

## Retention update — 24 September 2026

The product owner requested full-history synchronization. The current deployed rules
and privacy functions retain speech details until account deletion; the 365-day P-03
implementation described below is historical and has been superseded. Account deletion
and its retries remain active. The public policy now describes account settings,
selected reminder coordinates, unfinished drafts, detailed evaluations, and full-history
retention. See [deployment evidence](../full-account-sync.md#production-deployment--24-september-2026).

## Implementation review — 14 September 2026

The findings below are the original audit, not a description of the current code.

- **P-01 remains open.** Child accounts now use Google; PIN registration and PIN linking were
  removed by earlier security work. Google authentication and the QR handshake do not establish
  guardian consent. Per the product owner's decision, this change does not restrict child
  registration or features. An age question or email confirmation alone must not be represented
  as verified guardian consent. Controller details, lawful basis, processing region, applicable
  jurisdictions and consent design still need a factual/legal review; they are not invented here.
  The original legal discussion also needs qualification: the DPDP rules have phased commencement,
  and applicability/exemptions must be checked against the
  [official rules and enforcement timeline](https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa?pageTitle=Digit).
- **P-02 implemented in source.** Settings exposes the bundled privacy/deletion documents,
  an email fallback and a confirmed, reauthenticated deletion request. Instead of an additional
  callable SDK dependency, rules authorize a create-only `accountDeletionRequests/{uid}` document;
  an Admin SDK trigger performs erasure, with scheduled retries. The request blocks stale-token
  writes immediately. Nested user data, shared links/goals, legacy child codes, invitations,
  push registrations and user mirrors are removed; Firebase Auth is deleted last. The request
  barrier expires two hours after completion and is removed by hourly maintenance. Another
  linked Google account is never deleted merely because a self-asserted parent asks to delete
  their own account. Local history requires clearing app storage, as explained in the UI.
- **P-03 implemented in source.** Hourly, cursor-based maintenance clears cloud speech text and
  per-word results after 365 days, including restore-feed copies. It preserves session scores,
  times and counts. Rules prevent uploading expired speech; the client strips it from delayed
  uploads and accepts redaction during restore without accepting assessment tampering.
  Cleanup processes up to 4,000 records per collection group per run; monitor backlog and failures.
  This applies to all cloud accounts, avoiding age inference and retaining long-term aggregates.
  Firestore TTL deletes whole documents, so field cleanup uses transactions instead
  ([Firebase TTL documentation](https://firebase.google.com/docs/firestore/ttl)).
- **Other stale statements:** user documents are no longer client-deletable; push tokens are in
  `users/{uid}/private/messaging`, not readable by linked family accounts.

### Rollout and operations

Deploy `firestore:rules`, `firestore:indexes` and the two privacy functions (`eraseRequestedAccount`,
`maintainPrivacy`) together before releasing the Android build. Collection-group indexes must be
ready before maintenance runs. Functions require the existing Blaze environment. Publish the
updated privacy and deletion HTML with that release; the Android build packages those same sources.
The rules and both functions were deployed to `anushtanam-shastralife` on 14 September 2026,
with the product owner's approval. The live rules match the tested source. Both Node.js 22
functions are active in `us-central1`; account deletion retries are enabled and privacy maintenance
is scheduled every 60 minutes. Both retention collection-group indexes were verified `READY`.
No account deletion was manually requested as part of verification.
The deployment used the existing `ENFORCE_ADMIN_APP_CHECK=false` source default in a temporary
dotenv file, as in the previous security deployment. Existing admin functions were not redeployed.
Older Android versions must be updated to upload sessions older than 365 days: they do not
strip expired speech before writing, so the new rules reject those uploads. Coordinate that
client rollout with rule enforcement and maintenance activation. Existing scores/counts are preserved.

For a verified email request, run `node scripts/request-account-deletion.cjs PROJECT_ID VERIFIED_UID`
from `backend/firebase` to check the account, then add `--execute` to enqueue it. Verify requester
authority independently; possession of a family link alone is insufficient. Check request state
becomes `complete`, monitor function errors and the hourly cleanup, and follow up within the
documented 30 days. Maintenance cursors contain a document path until the next scan advances/reset;
these operational records are Admin-only. Existing device copies/backups are not remotely wiped.

### Validation

- Firebase Auth/Firestore emulator regression suite: **74 passed**, including fresh-auth and
  ownership enforcement, stale-token barriers, linked-account isolation, interrupted erasure,
  canonical/feed redaction, re-upload rejection and multi-page retention cursors.
- `:core:data:testDebugUnitTest` and `:app:compileDebugKotlin`: passed. The new retention tests
  cover the exact age boundary and reject changes to scores, timestamps or nonempty transcripts.
- Four `AccountDeletionViewModelTest` tests passed: canceled verification, wrong-account/auth
  failures, fresh verification before submission and retries of accepted requests.
- `:app:mergeDebugAssets`: passed; both published HTML sources are packaged for offline access.
- UI copy was revised to use simple English, with no em dashes in the privacy strings or bundled
  documents. The section uses the existing Lotus card, spacing, typography and theme colours.
  Confirmation text is split into short paragraphs and scrolls with larger text. Help email is
  separate from requesting deletion. Offline document pages also follow the active theme.
- The refined UI compiled and all four deletion-flow tests passed again. Both HTML sources were
  bundled successfully. No Android device or configured emulator was available for a visual review.
- Backend deployment was verified against the live rules release, function status and scheduler.
  Android UI changes and the revised public HTML still need distribution/publication.

---

**Artifacts reviewed:** `playstore-assets/privacy-policy.html` (updated 8 Sep 2026),
`playstore-assets/delete-account.html` (updated 23 Jul 2026), the storage layer, and the
data flows in `core/data`.

This app processes **children's personal data**, including speech-derived content and a
daily behavioural timeline, and discloses part of it to a second party. That places it in
the most heavily regulated category of consumer app.

## Starting point: the published documents are good

This needs saying before the findings, because it changes what they are.

The privacy policy is **materially better than most apps of this size**. It is specific
rather than boilerplate: it has a per-field table naming each data element, its purpose
and its storage location; it correctly distinguishes audio (not stored) from transcripts
(stored and synced); it has a dedicated children's-privacy section that accurately
describes both the PIN and QR linking flows including the five-minute expiry and
single-use semantics; it states plainly that there is no advertising, no advertising
identifier, no analytics SDK and no data sale — all of which the code confirms.

The deletion page is equally concrete: a named process, a 30-day commitment, and an
itemised list of what is removed that correctly includes the non-obvious items
(conflict history, restore records, expired QR invitations and their usage records).

Both documents describe the system as it actually is. The findings below are about the
**gap between what is documented and what is implemented or enforced**.

---

## P-01 — No age gate or verifiable parental consent · **HIGH**

A child account is created with no adult involvement whatsoever:

```
core/data/src/main/java/com/mantrasiddhi/data/remote/FirebaseAuthRepositoryImpl.kt:167-175

override suspend fun signUpChild(displayName: String, pin: String): Result<AuthAccount> =
    accountResult {
        require(pin.length == FirebaseContract.PIN_LENGTH && pin.all(Char::isDigit)) { ... }
        require(displayName.isNotBlank()) { "Name is required" }
```

A display name and a six-digit PIN. There is **no age question, no parent email, no
consent step, and no verification anywhere in the flow.** The child then begins generating
records immediately.

The privacy policy's children's section (§6) describes *how children sign in* and *what a
linked parent can see*. It does not claim a consent mechanism exists — accurately, because
none does.

### What is collected about the child once registered

| Data | Collection |
|---|---|
| Display name (often a real first name) | `users/{uid}` |
| Verbatim **speech transcriptions** | `practiceRecords`, `recitation_sessions` |
| Per-word pronunciation scores | same |
| Timestamped daily practice timeline | `japaEntries`, `progress`, `sandhya_logs` |
| **Approximate GPS location** (if the optional path is used) | `sandhya_prefs` on device |
| Device push token | `users/{uid}.fcmToken` |
| Google name, email, photo (Google sign-in path) | `users/{uid}` |

### Why this is High rather than a documentation nit

Three regimes bear directly on it, and the exposure is not theoretical:

**India's DPDP Act 2023 (§9)** is the most relevant given the product — Sandhya Vandan,
Gayatri Mantra, a Hindi locale, an India-based publisher. It requires **verifiable
parental consent** before processing any data of a person under **18**, and it separately
prohibits "tracking or behavioural monitoring of children". This app records a minor's
daily religious-practice timeline and surfaces it to a supervising account. Both the
consent requirement and the monitoring prohibition are engaged.

**COPPA (US)** requires verifiable parental consent before collecting personal information
from under-13s, with an express carve-out for audio: voice recordings and their
transcriptions are personal information. The app stores transcriptions.

**GDPR Art. 8** sets the digital-consent age between 13 and 16 by member state and requires
consent to be given or authorised by the holder of parental responsibility.

The policy also gives no lawful basis for processing under GDPR/DPDP, no data-controller
identity beyond a name and Gmail address, no EU/UK representative, and no statement of
processing location — Firestore's region is not disclosed, and cross-border transfer is a
DPDP concern.

There is a second, subtler issue: **the "parent" role is self-asserted.** Any account can
register as `PARENT` (`firestore.rules:93-94` accepts any of the three roles at create),
and linking proves only possession of the child's ID and PIN, never that the linker is the
child's guardian. The system's supervisory relationship is built entirely on an
unverified claim.

**Remediation.**
1. Add a neutral age screen before child registration.
2. Require a parent email and a verifiable consent step — a confirmation link, or making
   the parent account the *only* way a child profile can be created. The existing QR flow
   already demonstrates a child-initiated, parent-completed handshake; consent can ride on
   the same rails.
3. State the lawful basis, controller identity, processing region and retention period in
   the policy.
4. Get the children's-data position reviewed by counsel familiar with DPDP §9 before wider
   distribution. This is the one finding in the report where the right next step is legal
   advice, not a code change.

---

## P-02 — Deletion is documented but not implemented in-app or server-side · **MEDIUM**

The deletion page describes a complete and correct process. What is missing is the
machinery behind it.

**No in-app path.** `SettingsScreen.kt` offers theme, script display and profile
switching. There is no "Delete account" entry, and no link to the privacy policy or the
deletion page anywhere in the app — a repository-wide search for `delete_account`,
`deleteAccount` or `privacy` in the UI returns nothing. Google Play's User Data policy
requires apps that support account creation to provide **both** a web deletion link *and*
an in-app path to request deletion. The web half exists; the in-app half does not.

**No server-side deletion tooling.** The rules make client-side deletion impossible by
design — `allow delete: if false` on `japaEntries`, `japaGoals`, `practiceRecords`,
`japaConflicts`, `backupChanges`, `goals`, `links`, `childCodes` and `familyInvites`. That
is correct for integrity, and it means erasure **must** run through the Admin SDK. No such
function or script exists in this repository. The documented 30-day commitment currently
rests on manual console work.

**Partial automation does exist**, and deserves credit: `users/{uid}` is
owner-deletable (`firestore.rules:99`), and `deleteMessageInstallations`
(`functions/messages.js:368-381`) triggers on that deletion to clean up push registrations
and test-device entries, with `retry: true`. The pattern is right — it just covers one
collection out of a dozen.

**`childCodes` is undeletable**, so a deleted child's 12-digit code can never be released
or reused, and the record `{code → uid}` persists indefinitely. Under an erasure request
that is a residual identifier.

**Remediation.**
1. Write a `deleteAccount` callable that fans out across every collection listed on the
   deletion page, using the Admin SDK, and deletes the Firebase Auth user last. Make it
   idempotent and resumable.
2. Add an in-app "Delete my account" entry in Settings that calls it (with
   re-authentication — see [A-05](01-authentication-and-identity.md)).
3. Link the privacy policy and deletion page from Settings.
4. Add the `childCodes` document to the deletion fan-out.

---

## P-03 — No retention limit on cloud practice history · **LOW**

`users/{uid}/practiceRecords` and `users/{uid}/japaEntries` grow without bound. Every
session ever recorded — transcription, word scores, timestamp — is retained for the life
of the account. `backupChanges` retains a parallel feed. None of the rules express a TTL,
and no Firestore TTL policy appears in `firebase.json`.

For an adult tracking a years-long Purashcharana vow, indefinite retention is the whole
point of the feature and is legitimate. For a **child's speech transcriptions**, it is
harder to justify: the policy commits to using the data "only to provide the app's
features", and a five-year-old transcription serves no feature. Data-minimisation
principles under GDPR Art. 5(1)(e) and DPDP call for retention no longer than necessary.

Note the design already separates these concerns well — `practiceRecords` is owner-private
even from a linked parent (`firestore.rules:301-302`), while only a compact `progress`
summary is shared. The aggregate is what the product actually needs long-term.

**Remediation.** Set a retention period for `transcription` and `wordScoresJson`
specifically — say 12 months — after which the row keeps its scores and timestamps but
drops the speech-derived text. A Firestore TTL policy or a scheduled function can do this.
State the period in the policy.

---

## Data flow summary

Worth having in one place for future privacy reviews.

**Stays on device:** raw audio (never written to disk or uploaded — confirmed, the
transcription path consumes the PCM buffer in memory), on-device speech recognition,
GPS coordinates (`sandhya_prefs` DataStore), solar-time calculations, the full Room
database.

**Leaves the device:** display name, role, photo URL, FCM token, link codes, goals and
reward descriptions, japa entries, practice records **including transcriptions**, progress
summaries, QR invitation records.

**Visible to a linked parent:** the child's `displayName`, `photoUrl` and `fcmToken` from
`users/{uid}`; the `progress/{uid}` summary; shared `goals`. **Not** `practiceRecords` —
correctly withheld.

**Third parties:** Google only, as processor — Firebase Auth, Firestore, Cloud Messaging,
Play Services (barcode scanning, credential manager). No advertising SDK, no analytics
SDK, no Crashlytics, no third-party network client. The dependency list in
`gradle/libs.versions.toml` confirms this: there is no tracking library in the build at
all. The policy's claim on this point is accurate.

**Caveat:** the [A-04](01-authentication-and-identity.md) PIN-linking flow gives a parent
the child's actual credentials, which lets them sign in as the child and read
`practiceRecords` directly — defeating the boundary the rules and this table describe.
Fixing A-04 is what makes "not visible to a parent" true in practice as well as in the
rules.
