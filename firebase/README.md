# Anushtanam — Firebase (Phase B) setup & deploy

The Android app (`shastralife.anushtanam`) is already wired to Firebase Auth,
Firestore, and Cloud Messaging. This folder holds the **server-side** pieces you deploy
from the Firebase console / CLI. The app compiles and runs without deploying the Cloud
Function — that step only adds instant background push.

## 0. Prerequisites
- The Firebase project that `app/google-services.json` points to.
- Firebase CLI: `npm install -g firebase-tools`, then `firebase login`.

## 1. Enable Email/Password authentication
Firebase Console → **Build → Authentication → Sign-in method** → enable
**Email/Password**. This backs both parent accounts (real email) and child accounts
(synthetic `c<12digits>@child.anushtanam.app` emails). No other providers are needed.

## 2. Create the Firestore database
Firebase Console → **Build → Firestore Database → Create database** → **Production mode**
→ pick a region (e.g. `asia-south1`). Collections (`users`, `childCodes`, `links`,
`goals`, `selfTargets`, `progress`, `purashcharana`) are created automatically by the app
on first write.

## 3. Deploy the security rules
The strict rules are in [`firestore.rules`](./firestore.rules).

Option A — Console: **Firestore → Rules**, paste the file contents, **Publish**.

Option B — CLI (from this `firebase/` folder):
```bash
firebase deploy --only firestore:rules
```
(You may need a `firebase.json` with `{ "firestore": { "rules": "firestore.rules" } }`
and to select the project with `firebase use <project-id>`.)

**Redeploy after any rules change.** The `purashcharana/{uid}` block is the newest
addition: until the current file is published, every Purashcharana mirror write is denied
by the server. Nothing in the app breaks — the mirror is best-effort and the count keeps
working from Room — but the sankalpa stays device-only and cannot be restored on a new
phone, which is the entire point of the feature.

## 4. (Optional, Blaze) Deploy the goal-push Cloud Function
Instant push when the child's app is closed **requires the Blaze plan** (Cloud Functions
billing). Without it, the app still notifies the child of new goals via a same-device
Firestore listener while the app is open.

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```
The trigger [`functions/index.js`](./functions/index.js) fires on `goals/{goalId}`
creation, reads the child's `users/{uid}.fcmToken`, and sends an FCM notification.

## Data model (kept minimal — operational data only)
- `users/{uid}`: `role` (`PARENT`|`CHILD`), `displayName`, `linkCode` (children),
  `fcmToken`, `createdAt`.
- `childCodes/{code}`: `{ uid }` — 12-digit code → child uid (uniqueness + parent lookup).
- `links/{parentUid}_{childUid}`: `parentUid`, `childUid`, `childName`, `parentName`,
  `status` (`PENDING`|`APPROVED`|`REJECTED`), `createdAt`, `updatedAt`.
- `goals/{autoId}`: `parentUid`, `childUid`, `rewardType`, `rewardDescription`,
  `targetRecitationCount`, `currentRecitationCount`, `isCompleted`, `createdAt`.
- `selfTargets/{uid}`: `targetRecitationCount`, `currentRecitationCount`, `rewardType`,
  `rewardDescription`, `isCompleted`, `updatedAt` — the user's own active self-set target,
  mirrored so it survives a reinstall. **Owner-private** (a parent sees a child's targets
  through `goals/` instead).
- `progress/{uid}`: compact progress summary — `totalSessions`, `correctSessions`,
  `scoreSum`, `bestScore`, and a rolling `daily` map keyed `yyyy-MM-dd` →
  `{ n, c, s }` (sessions / correct / score sum). Readable by the user and by a parent with
  an APPROVED link; writable only by the user.
- `purashcharana/{uid}`: `mantraAksharaCount`, `targetJapaCount`, `currentJapaCount`,
  `startedAtMillis`, `completedAtMillis` (absent while still in progress), `isActive`,
  `updatedAt`. **Owner-private** — not even a linked parent may read it.

### Why `purashcharana/{uid}` exists
A Purashcharana is one lakh japa for every akshara of the mantra — 24,00,000 japa for the
Gayatri, which is the work of years. Held only in Room, that number dies with a reinstall, a
"clear app data", or a lost phone, and there is no way to earn it back. So it is mirrored:

- **Room stays the source of truth.** The UI never reads Firestore, and a failed mirror write
  can never break japa counting. The doc is a best-effort backup, not a live store.
- **Absolute count, not a server increment.** Unlike `selfTargets`, the client writes the full
  local `currentJapaCount`. A doc that was never created (enrolled offline), a lost write, or a
  replayed one all converge on the right number, because the next write carries the whole total.
- **The write is monotonic, and this is load-bearing.** An absolute write is idempotent but not
  *ordered*: a device whose count is behind — a fresh enrol after an offline login, a phone
  restored from an old backup, a second device mid-catch-up — would otherwise overwrite the
  cloud total with its own smaller one, and there would be nothing left to recover it from.
  Reconciling on read cannot save that, because by the next login the higher number no longer
  exists. So every write runs in a **Firestore transaction** that reads the stored doc and keeps
  `max(stored, local)` for the count and the *earlier* `startedAtMillis`, whenever the stored
  doc is the same still-running sankalpa. A withdrawn or different vow is overwritten wholesale,
  so re-taking the sankalpa after withdrawing correctly starts again from zero. A withdrawal
  from a behind device still flips `isActive` to false while preserving the higher count.
  Because a transaction needs the server, this **cannot run offline** — deliberately: a plain
  `set()` would sit in Firestore's offline queue and land on reconnect carrying whatever stale
  count it captured, which is precisely the write that must never happen. Failing instead costs
  nothing; Room holds the truth and the next mirror carries the full total.
- **Throttled.** A write per japa would be 24,00,000 writes per sadhak. Enrol, withdraw, a
  milestone crossing and completion mirror immediately; routine japa mirrors at most once a
  minute, and a session end flushes. Because the write is absolute, a skipped write loses
  nothing permanently.
- **Monotonic restore on login.** No local sankalpa and a remote one exists → restore it
  wholesale. Local sankalpa with a *higher* remote count → raise the local count and keep the
  *earlier* `startedAtMillis` of the two. Remote lower or absent → do nothing. A cloud value
  never lowers a local count.

Full recitation analytics (per-session history and per-attempt detail) are intentionally
**not** synced — they stay on-device in Room. Only the compact `progress/{uid}` summary above
and the two sankalpa records (`selfTargets`, `purashcharana`) leave the device.
