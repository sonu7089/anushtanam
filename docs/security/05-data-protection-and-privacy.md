# 5. Data Protection and Privacy

[← Back to index](README.md)

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
