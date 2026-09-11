# Notification-only nudges

11 September 2026. Requested after MF-11.

Publishers can queue a short phone notification without publishing a message. Tapping opens Home. The nudge does not create a message-list entry or change A moment to reflect. There is no new Android panel, sending screen, or reader preference. The website chooses the sending mode; readers use the existing Teachings and messages setting.

## Contract

The existing `adminMessages` callable has a publisher-only `createNotificationCampaign` operation. It accepts `campaignId`, `title` (1 to 100 characters), `body` (1 to 280 characters), `destination: "home"`, `audience: "test" | "all"`, `sendAt`, `expiresAt`, and an IANA `timezone`. Empty text, unsupported destinations, and content IDs/revisions are rejected. It creates only a private campaign and audit entry. No published content, draft, revision, or publication job is created.

Campaigns use `kind: "nudge"`. Existing message-linked campaigns and their request identities are unchanged. Exact retries reuse the same campaign. Changed text, timing, audience, or mode under an existing ID is rejected. Cancel and create a new ID to correct a queued notification. Scheduling, pause, cancellation, bounded dispatch, retry limits, expiry, and provider result reporting are shared with message notifications.

The app registers the optional boolean capability `notificationOnly: true`. The Firestore registration rule permits this field only within the existing owner's record and checks its type. Old registrations without it remain valid for message notifications and are skipped for nudges. `estimateAudience` accepts `kind: "nudge"` to apply this capability filter. The website must not enable capability on behalf of old clients.

FCM uses a normal-priority data message containing the campaign ID, title, body, Home destination, schema version, and expiry. Android validates and stores it in the existing durable local queue. The delivery job can show it without fetching a content document. Permission, preference, channel state, active japa, expiry, and campaign deduplication are checked before posting. The notification uses an immutable, campaign-specific Home intent. Activity launch and new-intent handling share the validated route, preserve pending taps through sign-in, and clear a handled tap so it is not replayed after recreation.

The scheduler remains at every 30 minutes. Provider acceptance does not prove display or reading. Cancel or pause stops future eligible dispatch; it cannot recall text already delivered to a phone, including a nudge waiting locally for japa to end. A missed or dismissed nudge has no inbox entry.

The separate website needs the mode selector and form described in [the integration guide](mf-10-website-guide.md#notification-only-nudges). Its implementation is outside this repository. Offer pages, checkout, arbitrary URLs, and other in-app destinations are not part of this change.

## Verification

- Android build and instrumentation APK: passed.
- Domain: 358 unit tests passed. Includes standalone payload validation, expiry, and supported destinations.
- Data: 102 unit tests passed. Includes standalone delivery without a repository fetch, japa deferral, opt-out, and invalid/expired queued content.
- App: 79 unit tests passed.
- Firebase Auth/Firestore emulator suite: 47 tests passed. Includes registration ownership and type checks, publisher authorization, no published content, stable request identity, test audience isolation, old-client filtering, schedules, expiry, cancellation, pause, opt-out, and provider retry.
- Android instrumentation: seven tests passed, covering standalone and existing message notifications plus MF-11 reflection regressions. Verified persistent queued text, one-time display, title/body, immutable PendingIntent, channel, japa deferral, opt-out, expiry, and Home route validation.
- Android lint: zero errors; existing warnings remain.

Production FCM delivery and signed-in cold/warm Home navigation were not exercised by these tests. No live campaign or sample offer was created or sent. Local tests use an isolated signed-out Firebase instance.

Logs: `notification-only-build.log`, `notification-only-ui-tests.log`, `backend/firebase/notification-only-backend-tests.log`, and `backend/firebase/notification-only-rules.log`.

## Deployment

Firebase successfully deployed `adminMessages`, `tickMessages`, and the registration rules to `anushtanam-shastralife` on 11 September 2026. Both function updates completed successfully, and the rules release succeeded. The existing account-deletion trigger, family notification function, indexes, and 30-minute scheduler frequency were not changed.

Command run from `backend/firebase`:

```text
firebase deploy --project anushtanam-shastralife --only functions:adminMessages,functions:tickMessages,firestore:rules --non-interactive
```

Deployment log: `backend/firebase/notification-only-deploy.log`. The updated debug APK is also copied to the repository root as `app-debug.apk`. Install it and reopen the app so its registration advertises support before testing a standalone send. The separate website still needs the documented mode selector and form.

Post-deployment `firebase functions:list` confirmed `adminMessages` and `tickMessages` are both `ACTIVE` on Node.js 22 with the new source hash. The account-deletion function remains active with its previous source. Verification output: `backend/firebase/notification-only-live-functions.log`.
