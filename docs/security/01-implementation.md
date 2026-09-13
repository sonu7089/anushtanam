# Authentication and identity remediation

Implemented 2026-09-13. Scope: A-01 through A-05 in the authentication report.
Owner decision: children use Google only; there are no existing users to migrate.

## Changes

- Removed child ID/PIN signup, login, Google PIN setup, credential-linking service,
  numeric-ID sharing UI, and manual code lookup. Google child profiles open directly.
  Adult email/password authentication remains available.
- The child role can be created only by a Google-authenticated session. Password
  sessions of stored child accounts cannot use authenticated Firestore paths, even
  if somebody adds a password provider outside the app. Client profile deletion is
  denied so deleting/recreating the mirror cannot reset an immutable role.
- Retired `childCodes` reads and writes. New child accounts receive no numeric code.
  Existing optional `linkCode` model metadata is inert; it is not a credential.
- Removed PIN-based direct link approval. Family sharing uses the existing 256-bit,
  five-minute, single-use QR token. Creating a new token invalidates the previous one.
- Before creating a QR invite or approving a pending request, the child confirms their
  Google account. A different selected UID or cancellation cannot authorize the action.
  The refreshed Firebase token carries the new authentication time. Rules independently
  require Google `auth_time` less than five minutes old for invite/state creation and
  direct child approval. Token refresh by itself cannot satisfy this rule.
- Added Play Integrity App Check for release and benchmark builds and the debug
  provider only for debug builds. Initialization precedes Application Hilt injection
  and repository startup. No debug attestation secret is committed.
- Added the `ENFORCE_ADMIN_APP_CHECK` Functions parameter, default false for monitoring.
  It applies only to the admin callable, not background triggers. Existing admin-role
  checks remain mandatory. Enabling this parameter requires the separate website to
  send valid App Check tokens first.

## Deployment and console work still required

Repository changes do not modify the live Firebase project. A-03 is prepared for
rollout, not closed as an enforced production control.

1. Enable/register Play Integrity for the Android app in Firebase/Google Play using
   the correct Play app-signing SHA-256 certificate (not merely the upload certificate).
   Configure the verdict requirements for the actual distribution channels. Register
   debug tokens only in a development project; do not include them in release builds.
2. Ship the client and inspect App Check request metrics before enforcement. Confirm
   Google sign-in, Firestore reads/writes, QR linking, and a rejected unattested request
   using real devices. Emulator tests do not exercise Play Integrity or Google UI.
3. Enable Firestore App Check enforcement after legitimate traffic is verified. For
   Authentication, verify Identity Platform eligibility, integrate as required, and
   enable Auth enforcement separately. Do not assume Play Integrity support from minSdk.
4. Configure the external admin website's App Check provider. Set
   `ENFORCE_ADMIN_APP_CHECK=true` in the Functions deployment environment and deploy
   `adminMessages` after verifying that website's attested requests.
5. Enable/verify email enumeration protection and applicable Auth abuse controls in
   the console. Adult passwords still require project password-policy configuration.
6. Deploy `firestore.rules` before first release of Google-only child accounts. Do not
   ship an older PIN client against these rules. No credential migration was created.

The QR authorizes a lasting family relationship, so its screen tells the child to show
it only to the intended parent. Recent Google confirmation is not a guarantee that
Google will demand a password or biometric on every device; that challenge is controlled
by the credential provider.

## Verification

- Android Gradle checks passed: debug source compilation, release source compilation,
  app Android-test source compilation, and unit tests (367 domain + 142 data + 119 app
  = **628 passed**, zero failures/skips).
- Firebase Auth/Firestore emulator suite: **56 passed**, zero failures/skips. Used
  isolated local ports with project `demo-anushtanam`; no production data was accessed.
- Coverage includes Google-only child profile creation, denied password child sessions,
  denied PIN links and retired ID access, QR expiry/replay/rotation, stale/future/missing
  auth-time rejection, role deletion protection, Google confirmation cancellation and
  account mismatch, and Firebase token refresh after Google reauthentication.
- Git whitespace check passed. Device-test sources were compiled, not executed.
- Real Google credential UI, Play Integrity attestation, console enforcement and the
  separate admin website have not been tested or deployed.

## References

- [Play Integrity setup](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Debug provider](https://firebase.google.com/docs/app-check/android/debug-provider)
- [Auth App Check](https://docs.cloud.google.com/identity-platform/docs/admin/app-check-integration)
- [Enumeration protection](https://docs.cloud.google.com/identity-platform/docs/admin/email-enumeration-protection)
