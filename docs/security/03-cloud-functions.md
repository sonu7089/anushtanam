# 3. Cloud Functions

[← Back to index](README.md)

> Reviewed and remediated in source on 2026-09-14. See [implementation, audit
> corrections, tests and rollout requirements](03-implementation.md). The findings
> below are the original audit, not current closure status.

**Files reviewed:** `backend/firebase/functions/index.js` (65 lines),
`messages.js` (373), `message-policy.js` (43)

Five functions are deployed: `onGoalCreated`, `adminMessages` (callable),
`tickMessages` (scheduled), `deleteMessageInstallations` (Firestore trigger), and the
campaign dispatcher invoked from the scheduler.

## Overall assessment

`messages.js` is the strongest code in the repository. It is transactional throughout,
uses leases to prevent concurrent dispatch, is idempotent on retry, validates every input
through a dedicated `message-policy.js` module, and pins content by revision so an edit
cannot retroactively change what was sent. The findings below are refinements, not
structural problems — with the caveat that the **most privileged client of this API is a
website that is not in this repository and was not reviewed.**

---

## F-01 — Parent-controlled text is embedded into a child's push notification · **MEDIUM**

```
backend/firebase/functions/index.js:50-68

const reward = goal.rewardDescription
  ? `${emoji} ${goal.rewardDescription}`
  : "a reward";
const body = `Recite the Gayatri Mantra ${count} times to earn ${reward}.`;

await getMessaging().send({
  token,
  notification: { title: "New goal from your parent", body },
  data:         { title: "New goal from your parent", body },
  android: { priority: "high" },
});
```

`rewardDescription` comes straight from a client write to `goals/`, where the rule caps it
at **200 000 characters**:

```
firestore.rules:340

d.rewardDescription is string && d.rewardDescription.size() <= 200000 &&
```

Two problems.

**Reliability.** FCM caps a message payload at **4 KB**. A `rewardDescription` over a few
kilobytes makes `getMessaging().send()` throw. The error is unhandled — `onGoalCreated`
has no `try`/`catch` — so the function fails, Cloud Functions retries, and it fails again.
A single oversized goal becomes a permanently failing, billable retry loop.

**Content.** A linked parent controls, verbatim, up to 200 KB of text that is rendered as
a **high-priority push notification on a child's lock screen**, prefixed with the
system-supplied title "New goal from your parent". Nothing filters it. In a family-safety
product, an attacker-or-abuser-controlled push channel into a minor's device deserves a
bound and a filter, not just a size cap for FCM's sake.

Note the asymmetry with the notification-only path, which gets this right:
`policy.nudge` (`message-policy.js:37-40`) caps `title` at 100 and `body` at 280
characters and validates the destination. The goal path has no equivalent.

**Remediation.**
1. Cap `rewardDescription` in the Firestore rule at something aligned with the UI — 200
   characters, matching `policy.text(input.summary, 280)` in spirit. 200 000 appears to be
   a copy of the `wordScoresJson`/`transcription` cap and is almost certainly unintended
   here.
2. Truncate defensively in `index.js` before building the body.
3. Wrap the `send()` in `try`/`catch` so one bad goal cannot produce an infinite retry.
4. Consider not echoing free text at all — "You have a new goal from your parent" with the
   detail shown in-app is safer and leaks less on a lock screen.

---

## F-02 — Admin authorization rests on a claim with no provisioning path · **MEDIUM**

```
backend/firebase/functions/messages.js:13-19

function authorize(request, publishing = false) {
  const role = request.auth?.token?.messageRole;
  if (!request.auth || !(publishing ? ["publisher"] : ["editor", "publisher"]).includes(role)) {
    throw new HttpsError("permission-denied", "Your account cannot do that.");
  }
  return request.auth.uid;
}
```

The check itself is correct: it reads a **custom claim** (server-set, inside the signed
ID token, not client-modifiable), and it correctly distinguishes `editor` from
`publisher`, re-calling `authorize(request, true)` for every privileged operation.

The gap is everything around it:

- **No provisioning path exists in this repository.** Nothing sets `messageRole`. It must
  be granted out-of-band via the Admin SDK or `gcloud`. There is no record of who holds
  it, no revocation procedure, and no review process. For a claim that grants the ability
  to push a notification to **the entire install base**, that is a significant governance
  gap.
- **No App Check** (see [A-03](01-authentication-and-identity.md)). `onCall` functions
  support `enforceAppCheck: true`; the `options` object at `messages.js:11` does not set
  it. The callable is reachable from any origin by anyone who obtains a token bearing the
  claim.
- **`maxInstances: 3`** (line 11) bounds cost, but it also means the admin API is easy to
  saturate — three concurrent instances at 60 s timeout is a trivially achievable
  denial-of-service against the operators' own tooling.
- **The calling website was not reviewed.** It is the most privileged client in the
  system and lives outside this repository. A compromise of that site, or of a single
  publisher's session, means arbitrary push to every user.

**Remediation.** Commit an admin-provisioning script to the repo so role grants are
reviewable and auditable. Enforce App Check on the callable. Require MFA on every account
holding `messageRole`. Schedule a separate review of the admin website.

The `messageAudit` collection (`audit()` at `messages.js:20-22`) is the right instinct —
every mutating operation records `uid`, `action`, `target` and `at`. Keep it, and make
sure it is exported somewhere immutable.

---

## F-03 — Internal error messages reach the browser · **LOW**

```
backend/firebase/functions/messages.js:263-269

} catch (error) {
  if (error instanceof HttpsError) throw error;
  // Do not return database paths, provider details, or tokens to a browser.
  if (error.code) throw new HttpsError("internal", "We could not finish that action. Please try again.");
  throw new HttpsError("invalid-argument", error.message || "Check the details and try again.");
}
```

The intent is sound and the comment shows awareness. The `error.code` heuristic catches
Firestore and Firebase errors. But **plain JavaScript runtime errors have no `.code`**,
so a `TypeError` from a malformed input falls through the final line and its message is
returned verbatim — e.g. `Cannot read properties of undefined (reading 'locale')`, which
discloses internal structure and confirms which input reached which code path.

The validation errors this branch exists to deliver are all thrown by `message-policy.js`
as bare `new Error(...)` with user-facing wording. That is the distinction worth encoding.

**Remediation.** Throw a tagged class from the policy module:

```js
class PolicyError extends Error {}
```

then return `error.message` only for `error instanceof PolicyError`, and map everything
else to a generic `internal`. Log the real error server-side.

---

## F-04 — No rate limiting on expensive callable operations · **LOW**

Several operations are cheap to invoke and expensive to serve:

- `estimateAudience` (`messages.js:97-108`) reads **up to 1000** `messageInstallations`
  documents plus the entire `messageTestDevices` collection, per call. Unthrottled, this
  is 1000+ billed reads per request.
- `listContent`, `listDevices`, `listCampaigns`, `listHistory`, `campaignDetails` each
  read 50 documents plus, in `listDevices` and `campaignDetails`, a full
  `messageTestDevices` scan.
- `getContent` reads a draft, **50 revisions**, a published doc and a job doc per call.

Restricted to `editor`/`publisher`, so the blast radius is the operator population — but
a single compromised or careless admin session can generate very large read bills.

**Remediation.** Add a per-uid token bucket (a Firestore counter doc, or Cloud Armor in
front of the endpoint). Cache the `messageTestDevices` set rather than re-reading it
inside the dispatch loop (`messages.js:284`, re-read per campaign batch).

---

## F-05 — FCM tokens can be registered by a user who does not own them · **LOW**

`messageInstallations` create/update (`firestore.rules:22-33`) validates that the
**document's** `uid` matches the caller, and that the token is a non-empty string under
4096 bytes. It does not — and cannot — validate that the token actually belongs to the
caller's device.

So a user who obtains another device's FCM token (from a log, a shared device, a
compromised backup) can register it under their own installation document and receive
that device's campaign notifications. Conversely they can register garbage tokens to
inflate `failed` counts.

The dispatcher handles the benign case well: on
`messaging/registration-token-not-registered` it deletes the registration
(`messages.js:330-334`), and it re-checks eligibility per recipient inside a transaction
rather than trusting the enumeration snapshot.

Impact is low — the content is broadcast teachings, not personal data. Recorded for
completeness.

**Remediation.** Accept it, or verify token ownership through a callable that checks the
token against the caller's session before writing the registration.

---

## Reviewed and found sound

**Input validation is centralised and strict.** `message-policy.js` is a small, readable
module with no trust in its caller:

- `id()` enforces `^[A-Za-z0-9_-]{1,100}$` on every document ID before it reaches
  `db().collection(...).doc(...)`. This **blocks Firestore path traversal** — a `/` in a
  document ID would otherwise let a caller address an arbitrary collection. Every
  `ref()` call routes through it. This is the single most important validation in the
  file and it is done correctly.
- `text()` enforces both a length cap and a non-blank requirement, and trims.
- `content()` whitelists `type` and `art` against fixed arrays rather than blacklisting.
- `window()` requires `Number.isSafeInteger` on both bounds, `start < end`, `end > now`,
  and an upper bound of `8640000000000000` (the JS `Date` maximum) — closing the usual
  integer-overflow and far-future-timestamp tricks.
- Timezones are validated by constructing `new Intl.DateTimeFormat("en", { timeZone })`
  and letting it throw — using the platform's own IANA database rather than a regex.

**Concurrency is handled properly.** `dispatchCampaign` (`messages.js:273-350`) takes a
`randomUUID()` lease with a 9-minute expiry, re-verifies `c.lease === lease` inside every
per-recipient transaction, and clears the lease in a `finally`. Two concurrent scheduler
runs cannot double-send.

**Idempotency is real.** `createCampaign` and `createNotificationCampaign` store
`JSON.stringify(contract)` and reject a reused `campaignId` carrying different parameters
with `already-exists` — so a retried request returns the existing state instead of
creating a duplicate send. Per-recipient `dispatch` documents mean a mid-run crash resumes
rather than re-sends.

**Content is pinned by revision.** `createCampaign` requires the published or scheduled
projection to match `input.revision` exactly (`messages.js:141-144`), and the dispatcher
re-checks it per recipient and cancels with `reason: "content_unavailable"` if it drifted.
An editor cannot change what an in-flight campaign says.

**Kill switches exist.** `messageControls/sending.paused` is honoured both by the
scheduler before it starts (`messages.js:365`) and inside every per-recipient transaction,
so a pause takes effect mid-campaign. `cancelCampaign` works the same way.

**Tokens are stripped from admin responses.** `listDevices` destructures
`const { token, ...visible } = doc.data()` (`messages.js:220`) so FCM tokens are never
returned to the browser. That is a deliberate, easily-missed detail done right.

**The optimistic-concurrency check on drafts** (`messages.js:41-43`) compares
`expectedRevision` and aborts with a user-facing message rather than silently clobbering a
concurrent edit.

**`deleteMessageInstallations`** follows the user-deletion path and is explicitly
documented as retry-safe. The `for(;;)` loop terminates because each iteration deletes the
200 documents it just read.
