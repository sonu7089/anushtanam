# Child Google sign-in and family linking

Implemented as the requested extension to MF-07 on 8 September 2026.

## Account behavior

- Children can choose Continue with Google on their sign-in page. Each Google account belongs to one profile. An adult account cannot become a child account through that page, and a child account cannot become an adult account through the adult page.
- A new Google child account receives a unique 12-digit ID and chooses a 6-digit PIN before opening practice. Setup resumes after restarting the app. If the account role cannot be loaded, recovery asks the user to retry rather than opening an assumed profile.
- Existing children first sign in with their ID and PIN, then choose Link Google in Settings. Both sign-in methods use the same Firebase UID and local profile. Their ID, name, history, goals and family links remain attached to that profile.
- An available Google photo appears on the child's profile, with an initial as the fallback. Adult local-profile import is never offered to a child account.

## Connecting a family

On the parent's Family page, choose Scan child's QR code or enter the child's ID and PIN. Both paths connect immediately. Neither requires another approval screen on the child's phone.

On the child's Family page, Show my QR code creates a new invitation. It contains a random 256-bit token, not a login credential. The server expires it five minutes after creation and permits one use. Making a new QR code invalidates the earlier one. The child's screen shows when the invitation is used. Connected parents also appear on the child's Family page.

The manual path verifies the ID and PIN with Firebase Authentication in a separate named Firebase session. The parent's main session stays signed in. The verification session signs out after the operation. PINs stay in memory while entered and are never written to Firestore, QR codes, Room, logs or saved form state. Firebase's authentication throttling applies to incorrect attempts.

Existing pending requests from earlier app versions can still be approved or rejected by the child.

## Server and storage

- `familyInvites/{token}` stores the child UID, child name, server creation time, and redemption account/time. Clients cannot list invitations.
- `familyInviteState/{childUid}` stores the current token. Only that child can read it. Rules consult it internally during redemption, so a parent cannot discover a QR token from a child UID.
- A Firestore transaction redeems the token and writes the approved family link together. Rules use `getAfter()` to require both writes and reject expired, revoked or already-used tokens. Concurrent scans cannot connect two parents with the same invitation.
- Direct PIN links are created using the verified child's password session. A parent cannot simply claim that a PIN was checked. Participant roles and document IDs are checked by the rules.
- `users.role` and an existing child ID cannot be changed by a client update. Child IDs are reserved atomically for Google sign-ups.
- No Room migration is needed beyond MF-07's version 10. QR invitations do not enable full practice-history backup.
- Expiry is enforced by the rules even while old invitation documents remain stored. Delete invitations and the child's active-token record when fulfilling an account deletion request.

## Required before a live release

1. Complete the [MF-07 Google OAuth setup](mf-07-implementation.md#firebase-setup-before-release), including Google provider enablement, signing fingerprints and the refreshed Android `google-services.json` with a web OAuth client.
2. Deploy `backend/firebase/firestore.rules` to the intended Firebase project. The previous rules do not support direct linking or QR invitations.
3. Test real Google accounts, including a supervised child account where available, on two physical devices. A child's Google family settings can require parental permission for third-party sign-in.
4. Verify that the Google scanner module is available on the device. If it cannot open, the Family page still offers ID and PIN linking.

No Cloud Function or Blaze plan is introduced by these linking flows. No production rules, privacy page or Firebase configuration was published by this change.

## Verification

Debug and release builds pass. Android lint reports no errors. The 467 unit tests pass (338 domain, 67 data and 62 app), along with 19 Android UI tests and 23 Firebase emulator tests. Real Google account selection and camera scanning on two physical devices still need the live setup described above.

Firebase emulator tests cover both orders of Google/password linking, repeated sign-in with either method, incorrect PINs, preservation of the parent's session, QR expiry and revocation, simultaneous redemption, restricted token discovery, forged links, role changes and old pending requests.

Android unit tests cover child PIN setup and recovery, cross-role rejection, same-profile Google linking, duplicate taps, scanner cancellation, account switching during a scan, QR payload validation, and encoding/decoding the actual QR matrix. UI checks exercise child sign-in, Google linking in child Settings, both Family linking choices, and the visible QR explanation.

Run Android checks from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
```

Run server checks from `backend/firebase`:

```powershell
npm ci
npm test
```

These tests use the local `demo-anushtanam` project, with Auth on port 19099 and Firestore on port 18080. They do not access live accounts.

Debug review pages: `child-auth`, `child-google-pin`, `settings-child-google`, `family`, `family-child`, `family-child-qr`.

## References

- [Firebase account linking](https://firebase.google.com/docs/auth/android/account-linking)
- [Firestore rule conditions and atomic writes](https://firebase.google.com/docs/firestore/security/rules-conditions)
- [Google code scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner)
- [Third-party access for supervised Google accounts](https://support.google.com/families/answer/9204736?hl=en)
