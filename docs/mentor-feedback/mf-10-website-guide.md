# Teachings and messages: website integration guide

Updated 11 September 2026.

**Notification-only support:** The sending page now has two modes: **Message notification** and **Notification only**. Notification only sends a title and short text and opens Home. It creates no message-list entry or reflection. See [the notification-only API and website flow](#notification-only-nudges).

## MF-11: A moment to reflect

The existing Home reflection area now supports one mentor-managed note. Add a dedicated **A moment to reflect** editor to the website using the existing operations below and the fixed content ID `home-reflection`. Keep that ID for every weekly or monthly revision. This is separate from choosing which general message is pinned.

1. Load `getContent` for `home-reflection`. Start at `expectedRevision: 0` only when no draft exists. Otherwise use its latest revision.
2. Save an English `teaching` with title, summary, full body, optional attribution, and one supported artwork choice. Home keeps its existing text-only design; the artwork appears on the reading screen.
3. Preview it under **A moment to reflect**. Home shows the full body up to 600 characters; longer bodies use the summary with Read more. Aim for a short title and one gentle paragraph. The reading screen holds the full text.
4. Publish the saved revision now, or choose the next week's or month's start and end dates. The existing scheduler checks every 30 minutes. Keep the current note valid until the planned replacement is due. A gap falls back to the built-in notes.
5. Use the same ID to revise, unpublish, cancel its pending schedule, or restore an earlier saved revision. Only one future replacement can be queued for this ID at a time.

Saving and publishing do not send a notification. The reflection is also readable in Teachings and messages. Home reads the dedicated document regardless of list order or pinning. Existing MF-10 apps continue to show it in their message list; the MF-11 Android release adds the Home placement.

Use the same role-checked callable API. No new Firebase deployment or direct database editing is needed. The website is maintained separately from this repository.

**Editorial tone:** Write as a caring teacher speaking to one person. Use simple English, short sentences, and familiar words. Avoid em dashes, slogans, grand promises, pressure about counts, and invented scriptural quotations. Keep Sanskrit or IAST when the teaching needs it, with a brief explanation. Attribution must name the actual author or source; leave it blank when none applies. Review the mentor's words before publishing. The app displays them as supplied rather than rewriting them.

Original sample copy for a preview, not a scriptural quotation:

> **There is time to begin again**
>
> If you have missed a few days, you can begin again today. Sit for a little while and say the mantra with care. You do not have to make up for everything at once.

These MF-11 decisions replace the earlier proposal for a separate featured Home panel, remote artwork on Home, and an additional Home refresh control.

This guide is for the private publishing area on your Anushtanam website. The Android app and Firebase backend are in this repository. The website page is a separate project that you will build and host.

## 1. What the app does

- Home has a **Teachings and messages** entry below **A moment to reflect**.
- That entry opens a list of current messages. Selecting a message opens a full reading page.
- A notification opens the same reading page, including from a closed app. After sign-in, a pending notification link continues to its message.
- Settings has **Teachings and messages** immediately above **Backup & Sync**.
- The preference starts on. Android asks for notification permission on the first opening of the app. If permission is declined, the preference is off. Tapping the toggle to turn it on requests permission again when Android allows a popup. Otherwise, it opens the relevant Android notification settings. There is no separate settings link.
- There are no quiet hours and no daily message limit.
- During an active japa session, received messages wait on the phone. They can appear after the session ends if they are still current. The app checks permission and the preference again before showing them.
- Messages remain readable when notifications are off. An internet connection is needed for new content. Valid saved content remains available offline.
- A missing or expired message gives the reader a short explanation. The reading page uses Back to return, without extra refresh or current-message buttons.
- The list has a refresh icon beside **Teachings and messages**, matching the Settings sync control. Each refresh starts a 10-second cooldown, including failed attempts. The shared repository reuses the first page during that window, including after leaving and returning to the list in the same app process.
- The list uses a new generated image of handmade paper and jasmine in morning light. Individual message pictures still use the four supported content choices below.

The current app's Japa route uses its recitation session state. The notification gate follows that state, including stop, error, completion, and ViewModel cleanup. A future separate mental-japa session implementation must use the same `TeachingNotifications.setPracticing` gate for its full session lifetime.

## 2. The page to build

Keep this area separate from your public website. Use Firebase Authentication from the **same Firebase project as the app**. Hiding a link is not access control. Every private operation goes through the `adminMessages` callable function, which verifies the signed-in account and its role.

Suggested navigation:

| Page | What it contains |
| --- | --- |
| Messages | Saved drafts, revision number, currently published version, and any scheduled publication |
| Write a message | Content fields, save state, and a preview beside the editor |
| Publish | Selected revision, start and end dates, timezone, and a deliberate publish button |
| Send a notification | Selected published revision, test/general audience, audience estimate, send time, expiry, and final review |
| Sending history | Queued and completed campaigns, provider results, retry information, and cancellation |
| Test phones | Registered installations that a publisher has explicitly marked as test phones |
| Sending controls | Pause or resume future notification work |

On narrow screens, put the preview below the editor. Use clear labels and ordinary buttons. A publisher should be able to tell whether they are saving, publishing, or sending before pressing a button.

### Roles

The trusted server assigns a custom Firebase Auth claim named `messageRole`:

| Claim | Allowed actions |
| --- | --- |
| `editor` | Save drafts, read drafts and revisions, list content |
| `publisher` | Editor actions plus publish, unpublish, schedule, send, manage test phones, cancel, and pause |
| Missing or any other value | No admin access |

A parent account in the mobile app has no publishing rights. The website must not offer a way to assign these claims to itself. A trusted operator uses the Firebase Admin SDK and preserves the account's other claims:

```js
const user = await admin.auth().getUser(uid);
await admin.auth().setCustomUserClaims(uid, {
  ...user.customClaims,
  messageRole: "publisher"
});
```

After a role changes, the website refreshes the ID token with `getIdToken(true)` or asks the administrator to sign in again. Never put a service-account key, Admin SDK credential, or FCM server credential in browser code.

## 3. Message fields

The first app release supports English plain text and four pictures bundled with the app. It does not download arbitrary images or render HTML. You can select an existing picture without uploading anything.

| Field | Meaning and validation |
| --- | --- |
| `contentId` | Stable ID, such as `begin-with-care`. Letters, digits, underscores, and hyphens only, 1 to 100 characters. Keep it when editing the same message. |
| `type` | `teaching`, `message`, or `announcement` |
| `locale` | `en` |
| `title` | Required, up to 100 characters |
| `summary` | Required, up to 280 characters. Used on the list and notification. |
| `body` | Required, up to 20,000 characters. Paragraphs are separated by blank lines. |
| `attribution` | Optional source or author, up to 160 characters. Leave blank for the app's “From Anushtanam” label. |
| `art` | `river`, `lotus`, `jasmine`, or `diya`. Default: `river`. |
| `pinned` | Boolean. Pinned messages appear before other messages. Within each group, newer messages come first. |

The server owns `schemaVersion`, `revision`, `editor`, and timestamps. Do not let the browser supply these as trusted metadata. Any unsupported content fields are omitted by the backend.

### Writing with warmth

Write as one person speaking kindly to another. Keep sentences short. Give the reader room to practice in their own way. Avoid guilt, urgency, spiritual promises, competitive language, em dashes, and long introductions. Check the source before attributing a teaching. Do not invent a quotation or an author's name.

Example:

```text
Title: Begin with care

Summary: You do not have to hurry through your practice. Give this moment your attention.

Body:
Some days, the mind settles easily. On other days, it wanders.

When you notice it wandering, return gently to the mantra. There is no need to be hard on yourself.

Take your time. Let the next repetition begin with care.

Attribution: Anushtanam
Art: river
```

This is original example copy, not a quotation from a scripture. It is included in the app's debug preview only. It is not published automatically.

### Preview

Show three views of the selected saved revision:

1. List card: title, summary, source, date, and pinned label.
2. Reading page: selected picture, title, source, date, full body, and the top Back control. There are no extra refresh or current-message buttons.
3. Notification: app icon, title, and summary. Explain that Android may shorten this text and that appearance depends on the phone.

Test narrow widths, dark backgrounds, long paragraphs, and large text. Keep text selectable on the reading preview. Use the app's existing warm colours and generous spacing as reference. The pictures are in `frontend/android/src/main/res/drawable/`; the mapping is in `presentation/components/LotusDesign.kt`.

## 4. Connect the website

Use the Firebase web SDK for Authentication and callable Functions. The function region is `us-central1`.

```js
import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFunctions, httpsCallable } from "firebase/functions";

const app = initializeApp(yourFirebaseWebConfig);
const auth = getAuth(app);
const functions = getFunctions(app, "us-central1");
const adminMessages = httpsCallable(functions, "adminMessages");

async function messageAction(input) {
  const result = await adminMessages(input);
  return result.data;
}
```

Add your website hostname to Firebase Authentication's authorised domains. Use your existing sign-in experience if it already uses this Firebase project. Otherwise, add a private sign-in page. Show “Your account does not have access to this page” when the role is missing.

The callable SDK sends the Firebase Auth token with each request. Use this API rather than an ordinary `fetch` request unless you deliberately implement Firebase's callable protocol. Public Firebase web configuration identifies the project; it is not a service-account credential.

### Error handling

| Error | What the page should do |
| --- | --- |
| `permission-denied` | Stop the operation and show that the account lacks access. |
| `aborted` on draft save | Reload the current draft and let the editor compare their changes. Do not overwrite silently. |
| `already-exists` on send | The campaign ID was reused with different details. Show the existing send. Use a new ID only for a deliberate new send. |
| `invalid-argument` | Show the validation message near the relevant fields. |
| `not-found` | Reload the list and explain that the item is unavailable. |
| Connection failure or timeout | Preserve the form. For campaign creation, retry with the exact same campaign ID and request. |

## 5. Exact API operations

Every request includes `op`. All dates below are Unix milliseconds. The browser must convert the editor's chosen date and IANA timezone to UTC. Show the chosen timezone beside the preview. An offset by itself is not enough for scheduling across daylight-saving changes.

### Save a draft

```js
await messageAction({
  op: "saveDraft",
  contentId: "begin-with-care",
  expectedRevision: 0, // 0 for a new draft; otherwise the revision you loaded
  content: {
    type: "message", locale: "en",
    title: "Begin with care",
    summary: "Give this moment your attention.",
    body: "Take your time. Let the next repetition begin with care.",
    attribution: "Anushtanam", art: "river", pinned: false
  }
});
// { contentId: "begin-with-care", revision: 1 }
```

Each successful save makes an immutable revision. It does not change the public version and does not send a notification. If a save response is lost, load the draft before retrying with a new revision number.

### Load and browse content

```js
await messageAction({ op: "listContent" });
// { items: [...], next: "last-content-id" | null }
await messageAction({ op: "listContent", after: "last-content-id" });

await messageAction({ op: "getContent", contentId: "begin-with-care" });
// { draft, revisions, published, scheduled }
```

Lists return up to 50 items per page. `getContent` includes the latest 50 saved revisions. A saved revision outside that list can still be published if its revision number is known. Content lists use ID order, so the page should not describe that order as newest first.

### Publish now or schedule publication

```js
const startsAt = Date.now();
await messageAction({
  op: "publish", contentId: "begin-with-care", revision: 1,
  startsAt, expiresAt: startsAt + 7 * 24 * 60 * 60 * 1000,
  timezone: "Asia/Kolkata"
});
// { scheduled: false, contentId: "begin-with-care" }
```

A future `startsAt` schedules publication. Only one pending publication is kept for a content ID; a new schedule replaces it. The currently published version remains until the scheduled replacement starts. The scheduler runs every 30 minutes. Scheduled publication and notification sending wait until the next run, normally up to 30 minutes after the chosen time, and may take longer during a service delay or a retry. Use an expiry that leaves enough time for a run. Publishing immediately still makes the message readable immediately; notification sending waits for the scheduler.

Choose an explicit end date for each publication and campaign. The app also checks expiry locally. There is no daily sending limit. FCM can hold an individual undelivered push for at most 28 days; the backend caps the provider's retention period accordingly, without shortening the content's publication window.

To restore an earlier version, use `publish` with that saved revision and a fresh validity window. Restoration does not send again.

```js
await messageAction({ op: "cancelPublication", contentId: "begin-with-care" });
// Cancels only the pending publication.

await messageAction({ op: "unpublish", contentId: "begin-with-care" });
// Removes the published version and cancels its pending publication.
```

Home publishing and notification sending are separate. A message-linked campaign (`createCampaign`) needs a published or scheduled readable destination. Every published message appears in the app's message list. A standalone notification (`createNotificationCampaign`) needs no content document and adds nothing to that list or to A moment to reflect.

### Choose test phones

```js
await messageAction({ op: "listDevices" });
// { items: [{ id, uid, enabled, appVersion, locale, schemaVersion, notificationOnly, updatedAt, test }], next }

await messageAction({
  op: "testDevice", installationId: "ID_FROM_LIST_DEVICES",
  enabled: true, label: "My Android test phone"
});
```

`listDevices` is publisher-only and does not return FCM tokens. Use `after` for another page. A fresh compatible app installation registers after sign-in and refreshes registration when the app resumes or its FCM token changes. The ID combines account identity with a random installation ID. Never type or paste a raw FCM token into the website.

Mark only phones you own or have agreed to use for testing. `enabled: false` on `testDevice` removes the test designation. The app's notification preference still applies to test phones.

### Estimate the audience

```js
await messageAction({ op: "estimateAudience", audience: "test" });
// { eligible, sampled, scanned }
```

Use `all` for the general audience. Eligibility requires a current enabled registration, supported schema, English content support, and a refresh within the last 30 days. Test sends also require an explicit test-device record.

For notification-only sends, pass `kind: "nudge"` to `estimateAudience`. Those recipients must also have `notificationOnly: true`, which the updated Android app registers automatically. Missing or false means the installed app does not advertise support. Do not set this field from the website or assume an old app can receive these nudges. Message-linked estimates may omit `kind` or use `kind: "teaching"`.

The estimate scans at most 1,000 registrations. If `sampled` is true, say “At least N eligible installations in the sample” and show the scanned count. Even a full scan is an estimate at that moment, not guaranteed delivery. One person can have several installations. A phone may also have account-specific registration records after account switching; the app suppresses repeated display of the same campaign.

### Send now or schedule a message notification

Show the final saved content, audience, estimated reach, send time, timezone, and expiry before the deliberate send action. Use different buttons for **Send to test phones** and **Send to readers**.

```js
// Generate once when the administrator confirms this particular send.
// Persist this request through timeouts and page reloads until its result is known.
const request = {
  op: "createCampaign",
  campaignId: crypto.randomUUID(),
  contentId: "begin-with-care", revision: 1,
  audience: "test",
  sendAt: Date.now(),
  expiresAt: Date.now() + 24 * 60 * 60 * 1000,
  timezone: "Asia/Kolkata"
};
await messageAction(request);
// { campaignId, state: "queued" }
```

Retry this exact request if the response is lost. Do not generate a new UUID on every click or retry. The same ID and same request return the existing campaign. The same ID with different details is rejected.

For a general send, choose `audience: "all"` and create a separate campaign ID after reviewing the test. The selected revision must be readable by `sendAt` and remain published through the campaign expiry. If it is unpublished or replaced before dispatch, remaining campaign work is cancelled. A corrected current message may be shown when an already-delivered notification is opened.

There are no custom HTML layouts, arbitrary links, automatic sounds, or counter actions in these payloads. Notification taps open the content ID inside the app.

### Notification-only nudges

On the sending page, let the publisher choose **Message notification** or **Notification only** before entering content. For Notification only, show:

- **Title:** required, up to 100 characters.
- **Notification text:** required, up to 280 characters.
- **Opens:** Home. This is the only supported destination in this release.
- Test or general audience, send time, timezone, and expiry.
- A phone notification preview and the final audience estimate.

Hide the message picker, full-body editor, attribution, artwork, and publish controls in this mode. Show a brief website explanation: “This appears as a phone notification and opens Home. It will not be added to the message list.” Do not add a new screen or toggle for this mode in the Android app. Readers control it with the existing Teachings and messages notification setting.

Keep the final buttons **Send to test phones** and **Send to readers**. Do not queue a campaign while the publisher is typing or previewing it. For a festival offer, first make sure the offer is actually available from Home; this feature does not create an offer or checkout page.

```js
await messageAction({ op: "estimateAudience", audience: "test", kind: "nudge" });

// Generate and persist this request once for the publisher's deliberate send.
const notificationRequest = {
  op: "createNotificationCampaign",
  campaignId: crypto.randomUUID(),
  title: "A little something for the festival",
  body: "Your festival offer is ready. Tap to take a look.",
  destination: "home",
  audience: "test",
  sendAt: Date.now(),
  expiresAt: Date.now() + 24 * 60 * 60 * 1000,
  timezone: "Asia/Kolkata"
};
await messageAction(notificationRequest);
// { campaignId, state: "queued" }
```

Do not include `contentId` or `revision`. Both are rejected for this operation. The publisher role is required. Text uses the same warm, simple English guidance as reflections. There are no em dashes in the sample. URLs and destinations other than Home are not supported.

An immediate send is queued for the next scheduler run, which checks every 30 minutes. A future `sendAt` schedules it. Choose an expiry that allows time for a run. Retry the exact saved request if the result is uncertain. Reusing its campaign ID with different text, timing, audience, or notification mode is rejected. To correct a queued nudge, cancel it and create a new campaign ID. There is no content revision or unpublish step for a standalone nudge.

After checking the test, make a separate deliberate request with a new campaign ID and `audience: "all"`. Estimate that audience with `kind: "nudge"` too. Existing pause, cancellation, campaign details, history, retry limits, and provider-acceptance reporting apply to both modes. In campaign history, label `kind: "nudge"` as **Notification only**; legacy campaigns without `kind` are **Message notification**. Display `title` and `body` for nudges instead of requiring a content ID.

Standalone nudges wait while japa is active, respect Android permission and the existing app/channel preference, expire on the phone, and suppress duplicate alerts by campaign ID. Received text is stored locally until delivery or expiry, so display needs no message fetch. Tapping opens Home from a running or newly launched app, waiting through sign-in if needed. The nudge is not kept as an inbox item. Cancellation cannot recall text already delivered to a phone or waiting in its local queue.

### Results, cancellation, and pause

```js
await messageAction({ op: "listCampaigns" });
// { items, next, paused }

await messageAction({ op: "campaignDetails", campaignId: request.campaignId });
// { campaign, attempts, next }

await messageAction({ op: "listHistory" });
// { items: [{ id, uid, action, target, at }], next }

await messageAction({ op: "cancelCampaign", campaignId: request.campaignId });
await messageAction({ op: "pause", paused: true });
await messageAction({ op: "pause", paused: false });
```

Use `after` to paginate campaigns, attempts, or action history. History is newest first. Campaigns and attempts use ID order. Poll a pending campaign at a modest interval, such as every 15 seconds while its page is visible. Stop polling when the user leaves or the campaign ends.

| State or count | Label to show |
| --- | --- |
| `queued` | Waiting to send |
| `sending` | Sending |
| `complete` | Sending finished |
| `cancelled` | Remaining sends cancelled |
| `expired` | Sending window ended |
| `attempted` | Sending attempted |
| `retry` | Waiting to try again |
| `accepted` | Accepted by the notification service |
| `failed` | Could not send |
| `skipped` | Not eligible when checked |

`accepted` is **not** proof that the phone displayed the notification or that someone read it. This implementation does not collect reading or opening events. Do not show “read”, “opened”, or a delivery rate derived from provider acceptance.

Cancellation and pause are checked before each recipient. A send already in progress may still arrive. Already displayed notifications cannot reliably be recalled. Pausing notifications does not pause scheduled content publication. Use `cancelPublication` or `unpublish` for that.

## 6. Backend and Android contracts

| Path | Access and purpose |
| --- | --- |
| `contentDrafts/{id}` | Private current draft |
| `contentDrafts/{id}/revisions/{number}` | Private immutable revisions |
| `publishedContent/{id}` | Public plain-text projection containing only reader-facing fields |
| `messagePublicationJobs/{id}` | Private pending publication |
| `campaigns/{id}` | Private campaign, request identity, progress cursor, lease, counters |
| `campaigns/{id}/dispatch/{installationId}` | Private provider attempt state |
| `messageInstallations/{id}` | Owner-scoped token, account ID, app version, schema, locale, preference, notification-only capability, refresh time |
| `messageTestDevices/{id}` | Private publisher-managed test audience |
| `messageControls/sending` | Private notification pause control |
| `messageAudit/{id}` | Private editor/publisher action history |

Firestore clients cannot publish or edit any admin records, even when the user has a publisher claim. The callable function uses the Admin SDK after checking the role. The published projection is public because these are general teachings. Do not publish personal, family, or account information there.

The Android feed fetches 30 records per page, pinned first and then newest first. The next page uses the last document as its cursor. First-page fetches share a 10-second cooldown in the repository, covering manual refresh, entry, and foreground return. Failed and cancelled attempts also reserve the cooldown. During that interval, an existing app process reuses its first-page result. Reading later pages is not blocked by the refresh cooldown. Server requests have an eight-second timeout and cached fallback. The app does not keep a permanent listener. Expiry is also checked while the screen remains open. This client cooldown limits normal repeated refreshes; it is not a server-enforced quota for modified clients.

The scheduler processes bounded batches, up to 50 installations per campaign pass and 10 due campaigns per run. This is an internal work-batch size, not a limit on how many messages you may send. A stable campaign ID and per-installation dispatch records keep retries attached to the same send. Transient provider failures get up to three attempts. Invalid tokens are retired. Provider acceptance followed by a server crash can lead to another provider attempt, so the phone also remembers campaign IDs until expiry to avoid repeated alerts.

FCM sends **data-only** messages with normal Android priority:

```json
{
  "kind": "teaching",
  "schemaVersion": "1",
  "campaignId": "the-campaign-id",
  "contentId": "begin-with-care",
  "expiresAt": "UTC_MILLISECONDS_AS_A_STRING"
}
```

Standalone campaigns send the following data instead. All FCM values are strings, and there is no `contentId`:

```json
{
  "kind": "nudge",
  "schemaVersion": "1",
  "campaignId": "festival-send-id",
  "title": "A little something for the festival",
  "body": "Your festival offer is ready. Tap to take a look.",
  "destination": "home",
  "expiresAt": "UTC_MILLISECONDS_AS_A_STRING"
}
```

Do not add an FCM `notification` object. Android can display those automatically in the background before the app checks japa or notification preferences. The app stores received work locally and checks permission, preference, session state, and expiry in an Android job before posting. Message-linked work resolves the readable content; standalone work uses its queued text without fetching a content document. All network work stays outside the short FCM receive callback.

Android job scheduling and normal-priority FCM are best effort. A quiet phone, an offline phone, battery restrictions, or a force-stopped app may receive a notification late or miss it. Published messages remain available through Home; standalone nudges have no saved inbox entry.

## 7. Setup and rollout

For notification-only support, deploy the updated `adminMessages`, `tickMessages`, and Firestore registration rules, then install the new Android APK. The app advertises `notificationOnly: true` when registration next succeeds. Keep older registrations unchanged. No new index, collection, scheduler, or deletion trigger is needed. See [the implementation and deployment record](notification-only-implementation.md) for verified status.

**11 September deployment:** These backend and rule updates are deployed on `anushtanam-shastralife`. The updated Android APK is ready. The separate website mode selector and form still need to be implemented there using this guide.

Before enabling the website option for general sends, verify an updated test phone appears with `notificationOnly: true`, preview the title/body, send a test campaign, and check Home navigation from a running and closed app. Confirm no message or Home reflection is created and test opt-out, japa deferral, expiry, cancellation, and a delayed or duplicate receipt.

The code in this repository does not deploy itself. No production notification is sent by this implementation task.

Deployment update, 10 September 2026: the message rules, indexes, and all three message functions are deployed to `anushtanam-shastralife`. Blaze is enabled. The live scheduler checks every 30 minutes. See [the deployment record](mf-10-firebase-deployment.md) before repeating setup. The Functions runtime is Node.js 22 and its dependency lockfile is included. The website account's publisher claim and authorised domain still need configuring when the separate website is connected.

1. Choose a staging Firebase project and confirm that its Android and website apps point to it.
2. Install the Functions dependencies in `backend/firebase/functions` with `npm install`. Pin the resulting lockfile in your deployment project. Check that the configured Node runtime is supported by Firebase before rollout.
3. Enable the required Firebase services, Cloud Functions, Cloud Scheduler, and FCM. Verify the project's billing and quotas. The scheduled backend requires a billing-enabled setup.
4. Deploy the included Firestore rules and indexes, and deploy `adminMessages`, `tickMessages`, and `deleteMessageInstallations` from `backend/firebase` using an explicit project ID. Avoid a default-project deployment.
5. Assign the publisher claim to your own trusted website account using the Admin SDK. Add your website's authorised domain.
6. Install the updated app on test phones. Grant notifications, sign in, and open Settings once so registration is refreshed.
7. Build the website page using the API contract above. Use supported bundled images in previews and avoid promising arbitrary image uploads.
8. Save and publish a sample in staging. Confirm it appears below the existing reflection entry without changing the APK again.
9. Register your test phones, estimate the test audience, and send a test campaign. Keep general-audience sending separate.
10. Check the full staged cycle below before deploying the same reviewed setup to production.

Suggested deployment command, after selecting and checking the intended project:

```text
firebase deploy --project YOUR_STAGING_PROJECT --only firestore:rules,firestore:indexes,functions:adminMessages,functions:tickMessages,functions:deleteMessageInstallations
```

The existing family-goal function is separate. Review any existing indexes before deploying the index configuration to a project with other infrastructure changes.

### If the app cannot load messages

The reader now says, “We could not load the messages. Please try again in some time.” This is a general loading error. It does not mean that the phone has no internet connection.

For the reading list, deploy the updated Firestore rules and the `publishedContent` index (`pinned` descending, then `publishedAt` descending). Wait until the index is ready in Firebase, then refresh the app. Missing rules or an index can prevent a successful read even when the phone is online. These are possible causes, not a confirmed diagnosis of a particular live project.

For publishing and notifications, also deploy the three functions above and the `campaigns` index. The website account needs its publisher claim and authorised domain. Publish a current message through `adminMessages` after setup. Collections are created as the backend writes records; you do not need to create empty collections by hand.

If no current message has been published, a successful read shows the ordinary empty state. If the loading error remains, check that the installed app points to the intended Firebase project and inspect the Firestore error for a permission, index, or service failure. Do not loosen the rules to allow unrestricted writes.

### Staging checks

- Publish, read, revise, restore an older revision, schedule, cancel a schedule, and unpublish.
- Confirm saving and publishing do not send a notification by themselves.
- Confirm a normal user and a parent cannot use the admin API or read private drafts.
- Confirm a test send excludes ordinary phones, even when they have notifications enabled.
- Check permission accepted, permission declined, app preference off, Android channel off, and returning from Android settings.
- Check japa in progress, session ended, process absent, foreground, background, warm tap, cold tap, and a tap after sign-in.
- Check duplicate receipt, a lost send response, provider retry, cancellation, pause, and expiry during japa.
- Check offline saved content, offline missing content, and a notification tapped after unpublishing.
- Check two phones, token rotation, account switching, and account deletion.
- Check large text, dark mode, TalkBack, and long English text on the actual installed app.

### Privacy and retention

The registration stores an account ID, installation token, supported schema, app version, English locale, notification preference, notification-only capability, and refresh time. Campaign history stores provider results and the title/body of standalone nudges. The app does not upload reading history. Pending notifications, standalone notification text, and duplicate IDs stay on the phone and are excluded from Android backup and device transfer. The random installation ID lives in Android's no-backup directory.

Include these records in your privacy and deletion procedures. Deleting the account's `users/{uid}` document triggers removal of its installation and test-device records. If your account-deletion service removes Firebase Auth accounts separately, make sure it also deletes that user document. A registration older than 30 days is ineligible. Invalid FCM tokens are removed when detected. Define an operational retention period for campaign logs and audit records before production rollout. Drafts and editorial history may need a different retention period. Do not describe ineligibility as automatic deletion.

## 8. Source references

- [Firebase callable functions](https://firebase.google.com/docs/functions/callable)
- [Firebase Android message handling](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Firebase scheduled functions](https://firebase.google.com/docs/functions/schedule-functions)
- [Firebase custom claims](https://firebase.google.com/docs/auth/admin/custom-claims)

Implementation entry points: `backend/firebase/functions/messages.js`, `message-policy.js`, `core/data/.../messages/`, and `frontend/android/.../screens/messages/`.
