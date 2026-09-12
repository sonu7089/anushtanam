# 1. Authentication and Identity

[← Back to index](README.md)

This is the most serious area of the audit. The child authentication scheme has a
structural weakness that cannot be fixed by adjusting a security rule.

---

## A-01 — Child accounts are protected by a 6-digit PIN with no lockout · **CRITICAL**

### The mechanism

A child account is a Firebase Auth **email/password** account where both halves are
synthesised from numbers the app generates:

```
core/data/src/main/java/com/mantrasiddhi/data/remote/FirebaseContract.kt:74-82

const val CHILD_EMAIL_DOMAIN = "@child.anushtanam.app"
const val CODE_LENGTH = 12
const val PIN_LENGTH  = 6

fun childEmail(code: String): String = "c$code$CHILD_EMAIL_DOMAIN"
```

Sign-in passes the PIN straight through as the Firebase password:

```
core/data/src/main/java/com/mantrasiddhi/data/remote/FirebaseAuthRepositoryImpl.kt:221-234

override suspend fun signInChild(code: String, pin: String): Result<AuthAccount> =
    accountResult {
        val trimmed = code.trim()
        require(trimmed.length == FirebaseContract.CODE_LENGTH && trimmed.all(Char::isDigit))
        require(pin.length == FirebaseContract.PIN_LENGTH && pin.all(Char::isDigit))
        val result = auth
            .signInWithEmailAndPassword(FirebaseContract.childEmail(trimmed), pin)
            .awaitResult()
        ...
    }
```

So the credential pair is: a **12-digit ID the child is told to share with a parent**, and
a **6-digit PIN**. The password space is 10^6 = **1,000,000**.

### Why the app cannot defend this

The check happens at Google's Identity Toolkit REST endpoint, not in the app. An attacker
never runs the app:

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<API_KEY>
{ "email": "c123456789012@child.anushtanam.app", "password": "000000",
  "returnSecureToken": true }
```

The API key is in `frontend/android/google-services.json`, committed to the repository
and extractable from any installed APK. Firebase API keys are identifiers, not secrets —
that is by design and is not itself the finding. The finding is what that endpoint will
do for an unauthenticated caller.

Three multipliers make this practical:

1. **No client-side control matters.** Anything the Kotlin code enforces — attempt
   counters, delays, input masking — is irrelevant to a direct HTTP client. The only
   present defence is Firebase's own anti-abuse, surfaced reactively as
   `FirebaseTooManyRequestsException`
   (`DirectFamilyLinkService.kt:113`, mapped to `FamilyLinkProblem.TOO_MANY_ATTEMPTS`).
   That protection is **per-IP heuristic**, not per-account lockout, and is defeated by
   rotating source addresses — a commodity capability.
2. **Weak PINs are accepted.** The only check is six digits
   (`FirebaseAuthRepositoryImpl.kt:169-171`). `000000`, `123456`, `111111` and birth-year
   patterns all pass. Published PIN-frequency studies consistently find the top ~20
   6-digit PINs cover a double-digit percentage of real choices. Against a population of
   children choosing their own PINs, the practical search space is **far below 10^6** —
   for many accounts it is a few hundred guesses.
3. **Account existence is free to probe.** Because the email is *derived* from the ID,
   `accounts:signInWithPassword` distinguishes `EMAIL_NOT_FOUND` from
   `INVALID_LOGIN_CREDENTIALS`, and `accounts:createAuthUri` confirms registration
   directly. An attacker can therefore enumerate **which** 12-digit IDs exist before
   spending any guesses on PINs — unless email-enumeration protection is enabled in the
   console (see *Verification* below).

### Impact

Full takeover of a minor's account. The attacker gains:

- The child's display name and profile.
- Their complete recitation history, per-session **audio transcriptions**, scores and
  daily activity timeline (`users/{uid}/practiceRecords`, `users/{uid}/japaEntries`).
- A precise daily/timestamped behavioural pattern — when the child is awake, practising,
  and using the device.
- The ability to write progress and to **approve a pending family link**, which under
  `firestore.rules:119-140` grants a parent account persistent read access to that
  child's `progress/{uid}` summary.

That last point is the one to sit with: **a PIN guess converts into a durable supervisory
relationship with a child.** The attacker does not need to keep the session.

### Remediation

Layered, in order of value:

1. **Raise the credential strength.** Six digits is not a password. Either move to a
   longer alphanumeric passphrase, or — better for the actual user population — make the
   PIN a *local device unlock* on top of a real credential (a parent-provisioned link, or
   Google sign-in, which the app already supports for children via
   `completeChildGoogleSetup`).
2. **Enforce per-account lockout server-side.** Firebase Auth does not provide this.
   Implement it: a callable Function that owns child sign-in, tracks failures per
   account, and applies exponential backoff and lockout — with the direct password
   endpoint disabled for the child domain.
3. **Reject weak PINs at enrolment.** Block sequential runs, repeated digits, and a
   blocklist of the most common 6-digit PINs. Cheap, immediate, meaningful.
4. **Enable App Check** (see A-03) so the password endpoint rejects callers that are not
   a genuine instance of the app. This does not fix the weak credential, but it removes
   the trivial scripted attack.
5. **Enable email-enumeration protection** in the Identity Platform console to close the
   free ID-existence oracle.

### Verification before closing

Check the Firebase console for: email-enumeration protection state, Identity Platform
abuse-prevention / reCAPTCHA enforcement on password sign-in, and any API key
restrictions on the Android key. Any of these being enabled reduces exploitability — none
of them raises a 6-digit PIN to an acceptable credential for a child's account.

---

## A-02 — The sharing identifier is also the auth username · **HIGH**

The 12-digit `linkCode` serves two incompatible purposes:

| Purpose | Requires |
|---|---|
| "Give this to your parent so they can link to you" | Being shared, shown on screen, read aloud, photographed |
| The username half of the child's Firebase credential | Staying confidential |

Every act of using the feature as intended discloses half the credential. Worse, the
disclosure is *durable and unrevocable*: `childCodes` is immutable by rule
(`firestore.rules:113` — `allow update, delete: if false`), and the users rule pins
`linkCode` once set (`firestore.rules:97`). **A child whose code leaks cannot rotate
it.** Their only remedy is a new account, abandoning their history.

Combined with A-01, disclosing the ID reduces the attacker's problem from "find an
account and guess a PIN" to "guess a PIN" — a search space of one million, in practice far
smaller.

**Remediation.** Separate the two roles. Keep the 12-digit code as a *public, rotatable,
short-lived* sharing handle — the QR invite flow in `DirectFamilyLinkService.createQrInvite`
already demonstrates exactly the right pattern (256-bit token, 5-minute lifetime,
single-use). Make the auth username an opaque non-shared value. At minimum, make the
sharing code rotatable.

---

## A-03 — Firebase App Check is not deployed · **HIGH**

No App Check dependency appears anywhere:

- Absent from `gradle/libs.versions.toml` (no `firebase-appcheck*` entry).
- Absent from `frontend/android/build.gradle.kts` dependencies.
- No `enforceAppCheck: true` on any callable in `backend/firebase/functions/messages.js`.
- No initialisation in `MantraSiddhiApplication.kt`.

Without App Check, **Firestore, Cloud Functions and Firebase Auth accept requests from
any HTTP client** presenting the public API key. There is no attestation that the caller
is a genuine, unmodified instance of the app on a genuine device.

This is the force multiplier for most other findings in this report:

- It is what makes the A-01 PIN attack a shell script rather than an app-automation problem.
- It is what makes R-01/R-02 (unvalidated collections) a scriptable storage-abuse vector.
- It is what makes R-06 (world-readable `publishedContent`) a billing-amplification target.

**Remediation.** Add `firebase-appcheck-playintegrity`, register the app with Play
Integrity, and enforce App Check on Firestore, Functions and Auth. Roll out in monitoring
mode first to size the impact on older installs, then enforce. Note that `minSdk = 33`
means the whole install base supports Play Integrity — there is no long tail to carry.

---

## A-04 — Parent-side PIN linking is a credential handover and a brute-force oracle · **MEDIUM**

To link by PIN, the **parent types the child's ID and PIN into the parent's own device**.
The app then signs in as the child in a secondary `FirebaseApp` to prove the credentials:

```
core/data/src/main/java/com/mantrasiddhi/data/remote/ChildPinLinker.kt:36-42

suspend fun link(parentUid: String, parentName: String, code: String, pin: String) = mutex.withLock {
    childAuth.signOut()
    try {
        val child = childAuth.signInWithEmailAndPassword(FirebaseContract.childEmail(code), pin)
            .awaitResult().user ?: throw FamilyLinkFailure(FamilyLinkProblem.INVALID_CREDENTIALS)
```

Two consequences.

**It is a credential handover, not a consent flow.** The parent ends up holding the
child's complete credentials and can sign in as the child on any device, at any time,
indefinitely — reading everything, including the owner-private `practiceRecords` that the
rules deliberately withhold from a linked parent (`firestore.rules:302`, "Full performance
records are private to their owner, including children"). The rules take care to give a
parent only a compact `progress` summary; this flow silently defeats that boundary. In a
family-safety context — estranged parents, coercive relationships, a sibling or friend who
shoulder-surfs — that distinction matters.

The credit is that the implementation is careful about what it *doesn't* break: a separate
`FirebaseApp` named `child-pin-link`, persistence disabled, `signOut()` in a `finally`, a
`Mutex`, and repeated `parentAuth.currentUser?.uid != parentUid` re-checks so the parent's
own session is never replaced. The design flaw is the flow itself, not its execution.

**It is a brute-force oracle inside the app.** `linkWithPin` performs an unlimited number
of child sign-in attempts, with distinguishable outcomes
(`FamilyLinkProblem.INVALID_CREDENTIALS` vs the rest), and there is no attempt counter
anywhere in the client — the only handling of `TOO_MANY_ATTEMPTS` is a string lookup at
`FamilyViewModel.kt:197`. Anyone holding a child's ID can grind PINs through the normal UI.

**Remediation.** Deprecate PIN-based linking in favour of the QR flow, which is already
built, already consent-based (the child initiates and displays it), and already
cryptographically sound. If PIN linking must stay, convert it to an *approval request*:
the parent enters the ID only, the child receives and approves the request on their own
device, and the PIN never crosses to the parent's phone.

---

## A-05 — No re-authentication for sensitive operations · **LOW**

`linkGoogle` (`FirebaseAuthRepositoryImpl.kt:296`) correctly calls `reauthenticate` when
Google is already linked. But no other sensitive operation requires recent
authentication: changing a child's PIN, approving a family link, or (once implemented)
deleting an account. An attacker with a borrowed unlocked phone can take persistent
actions without proving they know the credential.

**Remediation.** Require a recent re-auth for family-link approval and any credential
change. Firebase surfaces `ERROR_REQUIRES_RECENT_LOGIN`, which the code already maps at
`FirebaseAuthRepositoryImpl.kt:399`.

---

## Reviewed and found sound

Recorded so a future auditor does not re-derive it:

- **Code generation uses `SecureRandom`** (`FirebaseAuthRepositoryImpl.kt:77, 527-533`),
  with a leading non-zero digit so the code is always 12 digits. No `java.util.Random`
  anywhere in production code.
- **Role escalation is blocked.** `firestore.rules:95-97` pins `role` across updates, and
  `signInWithGoogle` (`FirebaseAuthRepositoryImpl.kt:265-273`) re-reads the stored role
  from the server with `Source.SERVER` and rejects a child/non-child mismatch — so
  choosing the child sign-in page cannot convert a parent's Google account into a child
  account. The comment at line 264 shows this was a considered decision.
- **Google ID tokens are verified by Firebase Auth**, not parsed client-side.
- **Sign-out is thorough** (`FirebaseAuthRepositoryImpl.kt:239-258`): local session
  cleared first, Sandhya alarms cancelled, all notifications cancelled, and Credential
  Manager state cleared under a timeout so an unavailable provider cannot trap the user.
- **The QR invite flow is genuinely well built** — see [Rules](02-firestore-security-rules.md)
  for the rule-side analysis.
