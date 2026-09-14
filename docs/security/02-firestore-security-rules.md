# 2. Firestore Security Rules

[← Back to index](README.md)

> Reviewed and remediated in source on 2026-09-13. See [implementation, corrections and rollout status](02-implementation.md). The findings below describe the original audit.

**File reviewed:** `backend/firebase/firestore.rules` (429 lines)

Because there is no application server, these rules are the **entire** authorization
layer for the product. Every finding here is directly reachable by any signed-in user
with an HTTP client.

## Overall assessment

The rules are **well above average**. Most write paths carry full schema validation,
field-level diff restrictions, and cross-document consistency checks. The author clearly
understood `request.resource` vs `resource`, `getAfter()` for atomic multi-document
invariants, and the `get`/`list` split.

The weakness is **inconsistency**: five collections received the full treatment, and four
did not. The gaps are where the rigour stops.

---

## R-01 — `selfTargets` and `purashcharana` accept arbitrary documents · **MEDIUM**

```
firestore.rules:398-408

match /selfTargets/{uid} {
  allow read, write: if isSignedIn() && request.auth.uid == uid;
}

match /purashcharana/{uid} {
  allow read, write: if isSignedIn() && request.auth.uid == uid;
}
```

Compare with `users/{uid}/japaEntries` a few lines earlier, which enforces a 13-field
schema, type checks on every field, revision monotonicity, and atomic feed publication.
These two collections enforce **ownership only**.

Consequences:

- **No schema.** Any field, any type, any nesting depth. `FirebaseContract.kt:56-71`
  documents the intended shape; nothing enforces it.
- **No size bound.** A caller can write up to the 1 MiB document limit, repeatedly, for
  every account they control. With App Check absent (A-03), that is a scripted storage-
  and-billing abuse primitive.
- **`write` includes `delete`**, so the "survives reinstall" durability guarantee these
  collections exist to provide is not actually protected.
- **Client trust.** `FirestorePurashcharanaSyncRepositoryImpl` and
  `FirestoreTargetSyncRepositoryImpl` read these back and feed them into domain logic.
  Malformed data written by a tampered client is parsed by a clean one after a device
  switch.

**Remediation.** Give both the same treatment the japa collections already have: a
`hasOnly` key list, per-field type checks, integer bounds on the count fields, and
`allow delete: if false` (or a narrow explicit delete). The helper functions
(`identity`, `count`, `millis`, `revision`) are already defined at lines 163-172 and can
be reused directly.

---

## R-02 — `progress/{uid}` create is unvalidated and parent-readable · **MEDIUM**

```
firestore.rules:416-428

match /progress/{uid} {
  allow read: if isSignedIn() && (
    request.auth.uid == uid ||
    isApprovedLink(request.auth.uid, uid)
  );
  allow create: if isSignedIn() && request.auth.uid == uid;
  allow update: if isSignedIn() && request.auth.uid == uid && ( ...version gating... );
  allow delete: if isSignedIn() && request.auth.uid == uid && !('sessionProjectionVersion' in resource.data);
}
```

The `update` path is carefully guarded — a monotonic `sessionProjectionVersion`, a
strictly-increasing `sessionProjectionRevision`, and a narrow exception for
`syncCheckpoints`/`updatedAt`. The comment at line 422 ("Cutover is permanent") shows
real thought about replay and corruption.

**The `create` path has none of it.** Line 421 checks ownership and nothing else.

Two consequences:

1. **The version gate is bypassable by deletion.** `delete` is permitted whenever
   `sessionProjectionVersion` is absent, and `create` is unconditional. A document that
   has not yet been migrated to v2 can be deleted and recreated with any content — the
   very replay the update rule was written to prevent.
2. **It crosses a trust boundary.** `progress/{uid}` is the one document a linked parent
   can read about a child. A tampered client can therefore write **arbitrary
   attacker-chosen fields of arbitrary type** into a document another user's app will
   fetch and render. Compose's `Text` is not an HTML sink, so this is not XSS — but it
   is unvalidated cross-user data flowing into parsing and UI code, and a type confusion
   or an enormous string is a plausible crash or DoS on the parent's device.

**Remediation.** Validate `create` with the same schema `update` implies: `hasOnly` on the
documented `FIELD_*` keys (`FirebaseContract.kt:46-54`), numeric bounds on
`totalSessions`/`correctSessions`/`scoreSum`/`bestScore`, a size cap on the `daily` map,
and `sessionProjectionVersion == 2` required on any new document. Then remove the delete
allowance or make it unconditional-but-final.

---

## R-03 — `users/{uid}` update validates only two fields · **MEDIUM**

```
firestore.rules:95-97

allow update: if isSignedIn() && request.auth.uid == uid &&
  request.resource.data.role == resource.data.role &&
  (!('linkCode' in resource.data) || request.resource.data.linkCode == resource.data.linkCode);
```

`role` immutability and `linkCode` pinning are correct and important. Everything else is
unconstrained: `displayName`, `photoUrl`, `fcmToken`, `photoSource`, `createdAt`, plus any
field the attacker invents.

This matters because `users/{uid}` is **cross-user readable** for approved links
(`firestore.rules:87-91`). Specifically:

- **`displayName`** is copied into `links` documents and rendered in the counterpart's UI.
  The `validParticipants` helper (line 60-63) even *depends* on it matching, so an
  unbounded value propagates. No length cap — a multi-megabyte display name is permitted
  up to the document limit.
- **`photoUrl`** is written with a `startsWith("https://")` check on the client
  (`FirebaseAuthRepositoryImpl.kt:340`), but the *rule* accepts anything. Today this is
  not exploitable: the UI only ever loads the **signed-in user's own** photo
  (`HomeScreen.kt:118`, `SettingsScreen.kt:86`), and `MantraComponents.kt:198` re-checks
  the `https://` prefix at the render site. But the rule permits a value that, if a future
  screen ever renders a linked counterpart's avatar, becomes an IP-disclosure and
  tracking-pixel vector aimed at a child's device. **Close the rule now, before the UI
  catches up with it.**
- **`fcmToken`** is exposed cross-user for no reason. The push path
  (`functions/index.js:38`) reads it with the **Admin SDK**, which bypasses rules
  entirely. No client ever needs to read another user's token. This is pure
  over-exposure.

**Remediation.** Add `hasOnly` on the known keys, a length cap on `displayName` (say 100),
an `https://` + length check on `photoUrl`, and move `fcmToken` out of the cross-readable
document — either into a `users/{uid}/private/` subcollection readable only by the owner,
or drop client read access to it.

---

## R-04 — A user with no `linkCode` can self-assign an arbitrary one · **MEDIUM**

The `linkCode` pin at line 97 is conditional:

```
(!('linkCode' in resource.data) || request.resource.data.linkCode == resource.data.linkCode)
```

If the **existing** document has no `linkCode`, the update may set it to any value. The
`childCodes` create rule then accepts that self-declared value through its second branch:

```
firestore.rules:108-112

allow create: if isSignedIn() && code.matches('[0-9]{12}') &&
  request.resource.data.keys().hasOnly(['uid']) && request.resource.data.uid == request.auth.uid &&
  (request.auth.token.email == 'c' + code + '@child.anushtanam.app' ||
    (getAfter(...users/$(request.auth.uid)).data.role == 'CHILD' &&
     getAfter(...users/$(request.auth.uid)).data.linkCode == code));
```

The **first** branch is sound — it ties the code to the authenticated synthetic email, so
a PIN-based child can only claim their own code. The **second** branch exists for
Google-based children (`ensureGoogleChildIdentity`, `FirebaseAuthRepositoryImpl.kt:373-393`)
and trusts a self-declared field.

So a child-role account without a code can claim **any unclaimed 12-digit code**, rather
than the `SecureRandom` one the client would have generated. Impact is bounded — existing
codes are protected by `allow update, delete: if false` (line 113), so this is squatting,
not hijacking — but it lets an attacker:

- Claim memorable or predictable codes (`100000000000`, sequential blocks) in bulk.
- Pre-claim a code to deny it to a legitimate user.
- Choose a code, which undermines the unpredictability the `SecureRandom` generator
  exists to provide.

**Remediation.** The cleanest fix is to make `linkCode` immutable once the user document
exists at all, and have the Google-child path allocate the code through a Cloud Function
with the Admin SDK rather than a client-declared field.

---

## R-05 — `childCodes` is readable by every signed-in user · **MEDIUM**

```
firestore.rules:105-107

match /childCodes/{code} {
  allow get: if isSignedIn();
  allow list: if false;
```

`allow list: if false` is correct and important — it prevents bulk enumeration. The
comment at 102-104 explains the reasoning honestly. But `get` is open to **any**
signed-in account, not just accounts with role `PARENT`, and it resolves a 12-digit code
to a Firebase `uid`.

Chained with the `links` create rule:

```
firestore.rules:129-135

allow create: if isSignedIn() && validParticipants(request.resource.data, linkId) &&
  request.resource.data.createdAt == request.time && (
    (request.resource.data.parentUid == request.auth.uid && request.resource.data.status == 'PENDING') || ...
```

any account with role `PARENT` can create a **PENDING link request against any child uid**
without that child's consent. The child sees an incoming request from an arbitrary
stranger's chosen `parentName` — which, per R-03, is an unvalidated free-text field.

That is an **unsolicited-contact channel aimed at minors**. The child must decline it,
and a persistent attacker can re-create it (`allow delete: if false` on line 141 means the
row persists, but a rejected link can be re-driven to PENDING... in fact the update rule
at 136-140 only permits the *child* to move it to APPROVED/REJECTED, which limits replay —
but the initial unsolicited request stands).

Exploitability is gated on knowing a code. Codes are 10^12, and `list` is blocked, so bulk
discovery is expensive — but codes are *shared by design* (A-02), appear in screenshots,
and are readable by anyone the child has ever linked with.

**Remediation.** Restrict `childCodes` `get` to `hasRole(request.auth.uid, 'PARENT')` at
minimum. Better: resolve codes through a callable Function that enforces per-caller rate
limits and logs lookups, so bulk probing is detectable. Consider requiring the child to
initiate all links (the QR flow already does this correctly).

---

## R-06 — `publishedContent` is world-readable · **LOW**

```
firestore.rules:18-21

match /publishedContent/{contentId} {
  allow read: if true;
  allow write: if false;
}
```

`allow write: if false` is right. Unauthenticated read is a deliberate product decision —
the comment at line 12-13 says so, and the content is generic teachings. Accepted as
designed.

The residual issue is **cost, not confidentiality**. `allow read: if true` with no App
Check means anyone who extracts the project ID (trivially, from `google-services.json` or
the APK) can issue unlimited billable reads. There is no Firestore-side spend cap, and
`listContent` in the admin API pages at 50, implying the collection is small enough that
a full scrape is cheap for an attacker and the cost lands on the project owner.

**Remediation.** Enforce App Check on Firestore even for the public collection; set a
Cloud Billing budget alert on the project.

---

## R-07 — Rules perform many document lookups per write · **LOW**

Firestore hard-limits a single-document request to **10** document access calls
(20 for a multi-document commit), and each is billed as a read.

The heaviest path is `links` create with a QR redemption, which chains:

- `validParticipants` (lines 58-66): 2 × `hasRole` + 1 × parent `displayName` + 1 × child
  `displayName` on the APPROVED branch = **up to 4**.
- `validQrLink` (lines 68-78): `get(familyInvites)` + `getAfter(familyInvites)` +
  `get(familyInviteState)` = **3**.

That is **7 lookups** for one write, before counting the `getAfter` the client transaction
triggers. Similarly, `japaEntries` create calls `published()` (line 200), which itself
does a `getAfter`, plus an `exists()` and an implicit `resource` read.

This is not a vulnerability. It is a **reliability and cost risk**: a future rule addition
on any of these paths can silently push a write over the limit, and the failure mode is a
permission denial in production that looks like an authorization bug. It also means every
family link costs ~7 billed reads.

**Remediation.** Add an emulator test that asserts the lookup count on the heaviest paths
(the repo already has `doc/perf/database-rules-audit.cjs` wired into `npm run test:perf`,
which is the right place). Treat the limit as a budget with headroom, not a ceiling.

---

## R-08 — No Cloud Storage rules in the repository · **LOW**

`google-services.json` declares a provisioned bucket:

```
storage_bucket: anushtanam-shastralife.firebasestorage.app
```

But `backend/firebase/firebase.json` deploys only `firestore` and `functions`, and the
only rules file present is `firestore.rules`. There is no `storage.rules`.

The app does not appear to use Storage today — no Storage dependency in
`libs.versions.toml`, no `FirebaseStorage` reference in the Kotlin sources. So this is
most likely an unused, auto-provisioned bucket.

The risk is that **an unused bucket may still be running the default rules**, and those
defaults have varied across Firebase project vintages — older projects defaulted to
"any authenticated user can read and write". Given A-01 (cheap child-account takeover)
and A-03 (no attestation), an authenticated-write bucket is a free file-hosting and
malware-distribution surface billed to this project.

**Remediation.** Check the bucket's live rules in the console. If Storage is unused, set
`allow read, write: if false;` and commit a `storage.rules` to the repo so it is version
controlled and deployed with everything else. Add `"storage"` to `firebase.json`.

---

## Reviewed and found sound

These paths were examined closely and are correct. Recording them so future work does not
weaken them by accident:

**The QR family-link flow** (`firestore.rules:68-78, 145-178`) is the strongest thing in
the file. It achieves single-use atomicity purely in rules:

- A 256-bit `SecureRandom` token, hex-encoded, matched against `[a-f0-9]{64}`.
- `getAfter()` asserts that redeeming the invite and creating the APPROVED link happen in
  the **same commit**, so neither half can be replayed alone.
- `request.time < invite.createdAt + duration.value(5, 'm')` enforces the lifetime
  **server-side**, not from a client clock.
- `familyInviteState/{childUid}.token == token` cross-checks that the invite is the
  child's *current* one, so an old token is dead even if the invite document survives.
- `allow delete: if false` with an explicit comment (lines 174-176) so a consumed invite
  cannot be deleted and recreated to make the same secret usable again. That is a subtle
  attack, anticipated.

**The `goals` collection** (lines 348-388) correctly splits three distinct update rights:
parent edits (requires an approved link), child progress updates (restricted to exactly
`currentRecitationCount` and `isCompleted` via `affectedKeys().hasOnly`), and no deletes.
`validAssignedGoal` enforces `isCompleted == (current == target)` so the flag cannot lie,
and `currentRecitationCount <= targetRecitationCount` so progress cannot overshoot.

**`practiceRecords`** (lines 300-346) is owner-private including from a linked parent —
a deliberate and correct data-minimisation choice, stated in the comment at line 301. The
`validRecord` function type-checks all three record variants and caps `wordScoresJson`
and `transcription` at 200 000 characters each.

**The `backupChanges` feed** (lines 380-393) requires every feed entry to equal the
canonical record written in the same commit, via `getAfter()`. The feed cannot drift from
the data it describes.

**`japaEntries` revision monotonicity** (lines 296-299): `revision >= resource.revision`,
`targetId` cannot be reassigned once set, and only a fixed set of fields may change.

**`links` `get` vs `list`** (lines 120-128) are split correctly — `get` uses a string match
on the composite ID (safe, since the ID is authoritative), `list` filters on
`resource.data`, which is the only correct way to write a listable rule.

**`messageInstallations`** (lines 22-33) prevents cross-user overwrite by checking
`resource.data.uid == request.auth.uid` on the existing document, caps `token` at 4096
bytes, pins `schemaVersion == 1` and `locale == 'en'`, and requires
`updatedAt == request.time` so the freshness window in `policy.eligible` cannot be forged.

**The test suite exists.** `backend/firebase/tests/` contains nine `.test.cjs` files —
`child-auth`, `family-rules`, `messages-rules`, `backup-rules`, `backup-validation`,
`session-counting` and more — run against the emulator via `npm test`. Any fix to the
findings above should land with a test in this suite. See
[S-03](06-supply-chain-and-build.md) on getting them running in CI.
