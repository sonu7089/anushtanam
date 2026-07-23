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
`goals`) are created automatically by the app on first write.

## 3. Deploy the security rules
The strict rules are in [`firestore.rules`](./firestore.rules).

Option A — Console: **Firestore → Rules**, paste the file contents, **Publish**.

Option B — CLI (from this `firebase/` folder):
```bash
firebase deploy --only firestore:rules
```
(You may need a `firebase.json` with `{ "firestore": { "rules": "firestore.rules" } }`
and to select the project with `firebase use <project-id>`.)

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

Recitation analytics are intentionally **not** synced — they remain on-device in Room.
