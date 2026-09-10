## MF-08 update, 10 September 2026

Automatic recovery now includes complete saved recitation performance, Sandhya observance and count-change history, japa contributions, and goal state. The old aggregate-only login restore is removed. The older architecture notes below describe the earlier mirror paths, which remain for existing family summaries and queued operations.

New owner-only paths under `users/{uid}`:

- `practiceRecords/{type}_{stableId}` stores full session scores, word results, transcription, duration and practice timestamps, observance records, and original count-change records.
- `backupChanges/{type}_{id}` is the incremental restore feed. Its contents must match the canonical record, and `changedAt` must be a server timestamp. Each page is committed with its local checkpoint.
- `japaConflicts/{id}` preserves both competing edits and their resolution. Entry `revisions` also retain rejected operations.

The owner confirmed publishing the updated `firestore.rules` on 10 September and seeing a successful sync at 10:38. The subsequent background and Sync now changes add no server fields and require no rules update. No live deployment was performed by the agent. This is a testing release with no migration requirement for an existing public user base. Use the current APK on every test device.

Run `npm test` for rule coverage. For Android recovery against local emulators, start an Android emulator, then run `npx firebase emulators:exec --config firebase.backup-test.json --only firestore,auth --project demo-anushtanam "powershell -NoProfile -ExecutionPolicy Bypass -File ../../scripts/test-backup-recovery.ps1"` from this directory.

Account deletion must recursively remove the entire `users/{uid}` tree, including entries, nested revisions, goals, practice records, conflicts and restore-feed records. Deleting only the parent document does not remove these collections. The existing manual account-deletion process must also remove the existing top-level family, progress, target and authentication records.

# Anushtanam — Firebase (Phase B) setup & deploy

## Child Google sign-in and direct family links

The [child sign-in and linking report](../../docs/mentor-feedback/child-google-family-linking.md) describes the latest account flow. Adults and children can use their own Google accounts. Each Google account has one profile. Children also keep a 12-digit ID and 6-digit PIN.

Deploy the current `firestore.rules` before using the new direct linking flow. Parents can connect by scanning a child's single-use QR invitation or entering the child's ID and PIN. This uses Firebase Auth and Firestore without a new Cloud Function. Old rules will reject these operations.

New collections are `familyInvites` and `familyInviteState`. QR expiry is enforced by server time, even if an expired document remains stored. Include these collections when deleting a child's account data. PINs are verified by Firebase Authentication and are not stored in these documents.

Enable Google in Firebase Authentication and refresh the Android OAuth configuration as described in the [MF-07 setup notes](../../docs/mentor-feedback/mf-07-implementation.md). Google sign-in does not work with an empty OAuth client list.

Run `npm ci` and `npm test` in this folder for the demo-project Auth and Firestore emulator tests. Auth uses localhost port 19099 and Firestore uses 18080. No live accounts are used.

The Android app (`shastralife.anushtanam`) is already wired to Firebase Auth,
Firestore, and Cloud Messaging. This folder holds the **server-side** pieces you deploy
from the Firebase console / CLI. The app compiles and runs without deploying the Cloud
Function — that step only adds instant background push.

## 0. Prerequisites
- The Firebase project that `frontend/android/google-services.json` points to.
- Firebase CLI: `npm install -g firebase-tools`, then `firebase login`.

## 1. Enable Email/Password authentication
Firebase Console → **Build → Authentication → Sign-in method** → enable
**Email/Password**. This backs both parent accounts (real email) and child accounts
(synthetic `c<12digits>@child.anushtanam.app` emails). Also enable Google for Google sign-in.

## 2. Create the Firestore database
Firebase Console → **Build → Firestore Database → Create database** → **Production mode**
→ pick a region (e.g. `asia-south1`). Collections (`users`, `childCodes`, `links`,
`goals`, `selfTargets`, `progress`, `purashcharana`) are created automatically by the app
on first write.

## 3. Deploy the security rules
The strict rules are in [`firestore.rules`](./firestore.rules).

Option A — Console: **Firestore → Rules**, paste the file contents, **Publish**.

Option B — CLI (from this `backend/firebase/` folder):
```bash
firebase deploy --only firestore:rules
```
The checked-in `firebase.json` supplies these paths. Select the intended project with `firebase use <project-id>`.

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
- `users/{uid}`: `role` (`PARENT`|`CHILD`|`INDIVIDUAL`), `displayName`, `linkCode` (children),
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

MF-08 supersedes the earlier aggregate-only backup scope. Complete saved performance now syncs through the private practice records described above.

## Teachings and messages

MF-10 adds the protected `adminMessages` callable, the `tickMessages` scheduler, and the `deleteMessageInstallations` user-deletion trigger. The website publishing area is hosted separately by the app owner. See [the website integration guide](../../docs/mentor-feedback/mf-10-website-guide.md) for exact request shapes, roles, staging setup, and delivery semantics. Deploy the new Firestore indexes as well as the rules. No production send is part of local verification.

Deployment update, 10 September 2026: Blaze is enabled and the message rules, indexes, and all three message functions are deployed to `anushtanam-shastralife`. The live scheduler runs every 30 minutes. See [the deployment record](../../docs/mentor-feedback/mf-10-firebase-deployment.md) for verification and update commands. The function dependencies are locked and use the Node.js 22 deployment runtime.
