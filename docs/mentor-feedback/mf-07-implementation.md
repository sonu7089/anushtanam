# MF-07: Google sign-in and profile photos

Implemented on 8 September 2026. Firebase setup and checks with real Google accounts are still needed before release.

The follow-up [child Google sign-in and direct family linking](child-google-family-linking.md) extends the original adult-only scope. It adds child Google accounts, ID/PIN setup, and direct linking by QR or ID and PIN. See that report for the newer behavior, server rules and checks.

## What people can do

- Adult and child sign-in screens show their fields, Login, then "or" and Continue with Google.
- New Google accounts use the role of the sign-in page. Returning accounts keep their existing role. Family linking supports direct QR and ID/PIN verification.
- Sign in with an existing password, then choose Link Google in Settings. Linking keeps the Firebase UID, local profile ID, chosen name, history and family links. An account collision gives a clear next step without moving data.
- See an available Google photo on Home and in Settings. An initial appears while the photo loads, when it is missing, or when the image cannot load.
- Choose whether to connect an older local profile with saved practice to the selected account. The app shows the destination email first. The existing profile row is reused, so retrying does not create another history. Profiles belonging to another account and unused demo profiles are excluded.
- Retry opening their practice after a profile-loading failure, without signing in again. Account details that could not be saved have a separate retry in Settings.
- Sign out and choose another account. The app clears the active local profile and Credential Manager session, dismisses notifications and stops reminders until another profile is active. Outbox rows retain their original owner. Old reminder actions cannot credit a different profile.

The new wording uses simple English without em dashes. Existing family and jasmine artwork is reused. The Google button uses Google's official logo. No generated illustration was needed for this change.

The sign-in role question and existing-account advice have been removed. Settings no longer shows the photo switch, linked-account message or backup explanation.

## Storage and backup

Room version 10 adds an optional unique Firebase UID, photo URL, photo source and photo preference to `user_profiles`. The migration keeps all existing tables and records. Room stores the owner association before the older DataStore mapping is updated. This prevents duplicate profiles if the app stops between those writes.

Existing local names and photo choices are preserved. Cloud `users/{uid}` documents can store `photoUrl` and `photoSource`. Their existing role and name are not replaced by a Google profile refresh. Current owner-only write rules already cover these fields; this change does not grant new access to family data.

This implements MF-07, not the full history backup work in MF-08. The existing active goal and Purashcharana mirrors remain available. Detailed practice history and Sandhya records stay on the phone. Connecting a local profile associates its existing history on this phone; it does not upload an entire history. There is no widget or japa-draft store in the current app to migrate.

The source privacy and account-deletion pages now describe Google identity and cached photo data. These pages still need to be published through the normal release process.

## Firebase setup before release

The refreshed `frontend/android/google-services.json`, supplied on 8 September 2026, includes three Android OAuth clients and one web OAuth client for `shastralife.anushtanam`. The generated debug sign-in client was checked against this file, and the rebuilt debug APK was copied to the project root as `app-debug.apk`. Provider settings and real account sign-in still need live verification.

1. Enable Google in Firebase Authentication for the app's existing Firebase project.
2. Register the signing fingerprints for the builds being tested and distributed. Use `./gradlew.bat :app:signingReport` for local debug and release builds. Also register the Google Play app-signing certificate from Play Console, since it can differ from the upload certificate.
3. Download the refreshed `google-services.json` for `shastralife.anushtanam` and replace the file in `frontend/android`.
4. Confirm that the matching Android client includes an OAuth client with `client_type: 3`. Gradle reads this web client ID into a directly referenced string resource, so release resource shrinking cannot remove it.
5. Rebuild and test with a new Google account and an existing email/password account. Verify that linking preserves the same UID, local history and approved family links. Check cancellation, an account already linked elsewhere, no Google account on the device, offline attempts, expired credentials, a missing photo and sign-out followed by a different account.

The app uses Credential Manager 1.3.0 and Google ID 1.1.1 with the existing Kotlin 1.9 and Firebase stack. See [Firebase Google sign-in setup](https://firebase.google.com/docs/auth/android/google-signin) and [Firebase account linking](https://firebase.google.com/docs/auth/android/account-linking). The Google button follows the [Google sign-in branding guidance](https://developers.google.com/identity/branding-guidelines).

## Verification

The debug build, release build with R8 shrinking, and Android lint pass. The unit test suites pass: 338 domain tests, 63 data tests and 51 app tests. Five database tests also pass on the API 37 emulator, including the version 9 to 10 migration and existing outbox checks.

The initial MF-07 checks passed all 14 selected UI tests, including Google configuration recovery and the existing Lotus UI tests. The former photo-switch test now checks the profile name and initial fallback. Lint had no errors; its existing warnings and dependency update hints remain.

After the sign-in and Settings simplification, the debug build and four focused UI checks pass. Two missing-configuration checks are skipped because the supplied Google configuration is now present.

Automated coverage includes credential cancellation and collisions, linking to the expected UID, keeping the chosen role/name/photo, explicit local-profile association, retry after an interrupted owner-mapping write, profile-loading recovery, and refusing a stale account.

The version 9 to 10 migration is tested on the Android emulator against Room's exported schema. The test checks parent and child profiles, a target count, a recitation session, a Purashcharana count, a Sandhya record, pending uploads and the sync deadline. Existing outbox tests cover separate account queues and retry safety.

Use the debug review pages `parent-auth`, `settings-google`, and `settings-link-google` to inspect the production components. The two Settings pages use local fixtures and make no account changes.

Screenshots: [sign-in, light](../ui-review/parent-auth-light-simple-signin.png), [sign-in, dark](../ui-review/parent-auth-dark-simple-signin.png), [link Google](../ui-review/settings-link-google-light-simple-signin.png), [linked profile, light](../ui-review/settings-google-light-simple-signin.png), [linked profile, dark](../ui-review/settings-google-dark-simple-signin.png). The linked profile fixture deliberately has no photo, so it shows the initial fallback.

Visual review uses a separate emulator data image. The existing development emulator had an unrelated schema with the same version number, so its database was left intact. The migration tests use the repository's exported version 9 schema.

The refreshed Firebase configuration is now supplied. Live provider settings, real account sign-in and photo downloads from Google still need verification.
