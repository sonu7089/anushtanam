# MF-10 implementation and verification

Updated 10 September 2026.

Follow-up UI changes are recorded in [message screen refinements](mf-10-ui-refinement.md): a refresh icon with a shared 10-second cooldown, fewer detail-page controls, permission requests from the notification toggle, and newly generated list artwork. These replace the earlier UI descriptions below where noted.

The Android message experience is implemented, and its Firebase rules, indexes, and three message functions are deployed on the owner's Blaze project. The live scheduler checks every 30 minutes. See [the deployment and live verification record](mf-10-firebase-deployment.md). The app owner will build and host the website publishing page separately using [the website integration guide](mf-10-website-guide.md). No website has been created and no production campaign has been sent.

## Accepted changes

- Home has a **Teachings and messages** entry immediately below **A moment to reflect**.
- It opens a paginated list of current messages. Each message has its own reading screen.
- The notification preference is immediately above **Backup & Sync**.
- The preference defaults on when Android notification permission is allowed. A declined first-run permission request turns it off. Android notification and channel blocks are reflected on resume. An explicit app opt-out is retained.
- There are no app quiet hours and no daily message limit.
- Scheduled publication and notification sending are checked every 30 minutes. Manual list refresh has a shared 10-second cooldown.
- Messages received during an active japa session wait until it ends. Expired messages are discarded. Permission and preference are checked again before display.
- Copy uses simple English without em dashes. Screens retain the existing typography, card shapes, and colours. The message list now has new generated artwork. Individual messages use the publisher-selected river, lotus, jasmine, or diya artwork.

## What is included

### Android

`TeachingMessage` and `TeachingDeliveryPolicy` define visibility and delivery checks. `TeachingRepository` reads a public projection with 30-record pages, pinned first and then newest first. It uses a bounded server fetch and Firestore's persistent cache for offline reads. Unknown schema/type, unsupported locale, malformed fields, future publication, and expired content are rejected.

`MessagesScreen` supplies the list, detail, empty, offline, and unavailable states. The list has a refresh icon beside its heading, with a shared 10-second cooldown also covering entry and foreground return. The reading page has its top Back control without extra refresh or navigation buttons. Loading-error copy asks the reader to try again in some time without assuming a lack of internet. The permanent practice guide and existing reflection content remain separate. The placeholder note is bundled locally, so an empty or unreachable content service leaves a useful page.

`TeachingNotifications` owns the separate notification channel, permission-aware preference, installation registration, durable local waiting queue, and campaign deduplication. `TeachingDeliveryJob` resolves content away from the short FCM callback and posts only after another local check. Jobs survive ordinary process recreation and reboot. Android's scheduling restrictions still apply.

`AppFirebaseMessagingService` routes only the new `kind: teaching` data messages into this flow and keeps the existing family-goal route. Notification PendingIntents target `MainActivity` with validated content IDs. Warm and cold intents use the same navigation path and can remain pending through sign-in.

The current Japa route uses the recitation session state. Start, stop, errors, completion, and cleanup update the delivery gate. If a separate mental-japa route is added later, its session lifetime must use the same gate. This task does not implement a new japa mode.

Notification queue data and preference state are excluded from Android backup and device transfer. Installation identity is stored in the no-backup directory. The app does not upload reading or opening events.

### Backend

- `adminMessages`: role-checked callable API for drafts, immutable revisions, publication, scheduling, unpublishing, rollback through an earlier revision, test-device management, audience estimates, campaign creation, cancellation, pause/resume, and history.
- `tickMessages`: scheduled publication, expiry, and bounded campaign dispatch. Per-campaign leases and per-installation records retain work across retries. Each recipient is checked again against preference, content availability, pause, cancellation, and expiry.
- `deleteMessageInstallations`: removes installation and test-device records when the account's user document is deleted.
- Firestore rules: public generic content, owner-only installation registration, and no direct client access to private publishing or sending records.
- Firestore indexes: pinned/newest feed ordering and due-campaign selection.

Provider acceptance is recorded separately from failure or retry. It is never reported as a person having read the message. Content edits do not create campaigns. Repeating a campaign request with the same ID returns the existing send; a changed request with that ID is rejected.

The supported content model is intentionally explicit: English plain text and four bundled image choices. Every published item appears in the message list. Arbitrary remote image uploads, hidden notification-only placement, executable content, additional languages, and individual reading analytics are not included.

## Verification completed

| Check | Result |
| --- | --- |
| Android debug APK | Built successfully |
| Domain unit suite | 355 tests, passed |
| Data unit suite | 92 tests, passed |
| App unit suite | 79 tests, passed |
| Android lint | Passed with 0 errors; 14 existing warnings and 3 hints |
| Backend policy and handler workflow tests | 9 tests, passed |
| Backend suite under Auth/Firestore emulators | 40 tests, passed, including the exact paginated query and denied private access |
| Android UI and notification tests | 7 tests, passed on `emulator-5580` |
| Visual review | Light/dark list and reading screens, Home entry, empty and offline states |
| Large text | Reading body and Back checked at 1.8 font scale in a 320 dp layout |

The notification instrumentation test uses an isolated signed-out Firebase instance. It checks japa deferral, persistent pending state, one-time display, the separate channel, immutable tap intent, duplicate receipt, opt-out clearing, permission denial, expired data, and invalid content IDs. It does not send through the live FCM provider.

Backend workflow tests exercise the exported handlers with in-memory Firestore/provider substitutes. They cover authorization, edit conflicts, revision retention, idempotent creation, test-audience isolation, cancellation, pause, opt-out, unpublishing, transient retry, invalid-token retirement, and scheduled publication/expiry. Firestore access rules are separately tested against a local emulator.

Relevant local logs: `mf10-build.log`, `mf10-final-build.log`, `mf10-backend-tests.log`, `mf10-rules.log`, and `mf10-ui-tests.log`. These generated logs are ignored by Git.

Visual references:

- [Home entry](../ui-review/home-messages-light-mf-ten.png)
- [Message list](../ui-review/messages-light-mf-ten.png)
- [Reading page](../ui-review/message-detail-light-mf-ten.png)
- [Dark message list](../ui-review/messages-dark-mf-ten.png)
- [Offline state](../ui-review/messages-offline-light-mf-ten.png)
- [Notification setting above Backup and Sync](../ui-review/settings-messages-light-mf-ten.png)

## Still required before production use

Build the separate website page and configure its authorised domain and publisher roles. Register test phones and verify a complete publish/send/revise/unpublish cycle through live FCM on compatible installed apps. The backend and indexes are already deployed. Quiet live checks verified public/private access and one empty scheduler run; they did not send to a phone. Test warm and cold notification taps, phone idle restrictions, token rotation, multiple installations, and the target manufacturers. Emulator tests do not establish delivery reliability on a physical phone.

Review billing, operational log retention, and the privacy description for installation registrations. Apply the user-document cleanup trigger to the actual account-deletion process. Stale registrations become ineligible after 30 days but are not automatically deleted just for being old.

The website guide gives the exact operations, examples, rollout steps, and expected limitations for this work.
