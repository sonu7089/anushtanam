# Remote notifications and Home content — MF-10 and MF-11

**11 September 2026 update:** MF-11 uses the existing **A moment to reflect** area for the mentor's weekly or monthly reflection. It adds no new Home panel and no refresh or update copy. The existing message list remains separate. See [the accepted implementation](mf-11-implementation.md) for the current scope and publishing contract; it replaces the earlier dynamic Home proposal below.

**10 September 2026 update:** MF-10 now has Android and backend source code. The owner will build the website publishing page separately using [the website guide](mf-10-website-guide.md). The accepted app preference starts on when Android notification permission is granted and is off after permission is declined. It sits above Backup & Sync. There are no quiet hours or daily sending limit. Received messages wait during japa. Home opens the message list from below “A moment to reflect.” These decisions replace the earlier proposed opt-in, quiet-hours, frequency-limit, and internally built admin-page requirements below. See [implementation and verification](mf-10-implementation.md) for scope and rollout status.

Prepared: 7 September 2026. Status: proposed work requested by the app owner. This document adds **Release C**, a required P1 stage, to the [change plan](mentor-feedback-plan.md). [Product requirements](mentor-feedback-product-spec.md) and [implementation work packages](mentor-feedback-implementation-plan.md) refer to this specification. Optional improvements move to Release D.

## 1. Intended outcome and current foundation

Developers should be able to publish a teaching or encouraging message on Home and optionally send it as a notification, without releasing another APK for each message. An initial app release must introduce this capability; later updates work within the content types and navigation actions that release supports.

Source findings:

- [SadhanaWisdom.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/content/SadhanaWisdom.kt) currently selects bundled teaching strings. There is no remote publishing workflow in this path.
- [HomeScreen.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt) and [HomeViewModel.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt) provide the integration point for published content and local fallback.
- [AppFirebaseMessagingService.kt](../../core/data/src/main/java/com/mantrasiddhi/data/remote/AppFirebaseMessagingService.kt) currently posts generic received messages through [GoalNotifier.kt](../../core/data/src/main/java/com/mantrasiddhi/data/notification/GoalNotifier.kt). It needs explicit announcement routing and a separate notification channel.
- [functions/index.js](../../backend/firebase/functions/index.js) contains an optional new-child-goal push trigger; it reads a single `fcmToken` from the child user document. It is not an admin campaign system and does not model several installations per user.

Live Firebase deployment/configuration was not inspected in this task. Reuse the existing Firebase foundation, but verify deployed services, SDK compatibility, infrastructure billing requirements, and permissions before implementation rollout.

## 2. User experience

### 2.1 Dynamic Home area — MF-11

- [ ] Add a Home section such as “Teachings and messages” containing a featured card and a small list or “View all” link.
- [ ] Supported first-release types: short teaching, encouraging message, and app announcement. A card contains a title, short text, optional approved image, attribution, and a “Read more” or supported in-app action.
- [ ] The full-content screen shows the complete text, source/author where applicable, publication date, and any supported action. Preserve Sanskrit/IAST rendering and accessibility.
- [ ] Keep the permanent MF-03 “Gayatri practice guide” separate and visible. Remote content cannot replace its teaching, hide its entry point, or mark it acknowledged.
- [ ] Developers can remotely change text, supported imagery, ordering/pinning, locale, visibility dates, and supported actions. New executable behavior, screens, or unsupported layouts still need an app update.
- [ ] Published messages can be read regardless of the “Teachings and messages” notification preference. A notification is a route to content, not its only storage location.
- [ ] Refresh on Home entry/foreground return with a bounded freshness interval; offer pull-to-refresh or a retry action. Do not require a permanent connection or block Home progress cards on a remote fetch.
- [ ] Show valid cached content offline. When none is available, show a bundled teaching; a content service outage must not break japa, counts, or reminders.
- [ ] Hide expired items using their validity window. On reconnect, apply unpublished/corrected content promptly. Content previously cached on an offline phone cannot be recalled immediately; do not promise instant removal everywhere.
- [ ] Unknown schema/type, missing translations, failed images, or malformed payloads fall back safely. Do not expose raw errors or force an update just to reach the counter.
- [ ] Allow the user to dismiss an ordinary featured message or mark it read without recording a spiritual judgement. Read/dismissed state is independent from practice-guide acknowledgement.

### 2.2 Developer announcements — MF-10

- [ ] Add “Teachings and messages” as a separate optional preference and Android notification channel, distinct from Sandhya reminders and family goals.
- [ ] Explain the category and let users opt in. Granting Android notification permission for Sandhya must not enable this category automatically.
- [ ] Proposed defaults: normal delivery priority, at most one general teaching/message per local day, and quiet hours from 9 PM to 7 AM, editable by the user. These defaults apply to developer messages, not Sandhya event reminders.
- [ ] Respect category opt-out and quiet hours when dispatching and again before local display. A newly queued message must not bypass a preference changed on the device while offline.
- [ ] Avoid interrupting an active mental-japa session; defer the optional announcement until the session ends and outside quiet hours, provided it has not expired. The Home feed remains available meanwhile.
- [ ] A notification contains a short title/body and opens the matching content. No automatic audio playback, microphone activation, or japa credit.
- [ ] Handle app foreground, background, process absent, and cold-start tap. Use content/campaign IDs to route consistently rather than always opening the launcher page.
- [ ] An expired, unpublished, removed, or incompatible destination shows a clear message and a link to the current feed. If offline, show a valid cached body or a retry state.
- [ ] Notification delivery is best effort; retain the teaching in the Home feed even when the notification is delayed, blocked, or missed.

Non-urgent teaching messages should use normal priority. Firebase documents that normal-priority messages can be delayed while a device is in Doze; high priority is intended for time-sensitive, user-visible messages. [FCM Android priority](https://firebase.google.com/docs/cloud-messaging/android-message-priority).

## 3. Developer/editor workflow

Provide a small protected admin interface rather than requiring edits to app strings or raw production database documents for every teaching. It can be a separately deployed internal web tool backed by the existing Firebase project. This plan does not create or deploy that tool.

1. **Draft:** choose type and locale; enter title, summary, body, attribution, optional image, and a supported destination.
2. **Preview:** view how the Home card, content page, and notification will appear, including long text and target app compatibility.
3. **Choose placement:** Home only, notification only with a readable content destination, or Home plus notification. Home publishing and notification sending remain separate explicit controls.
4. **Choose audience/timing:** opted-in compatible installations, locale/app-version filters if needed, and either publish/send now or a scheduled time with an explicit timezone.
5. **Test:** send to registered test devices/a test audience and preview Home against staging or an admin preview. A test must not reach the production audience.
6. **Publish/send:** show the final content, intended audience estimate, schedule, and expiry before the admin's deliberate publish/send action. This is a product control for future administrators, not a request for approval in this documentation task.
7. **Manage:** edit/revise, unpublish, restore an older approved revision, cancel unsent work, or stop remaining campaign dispatch. Display the result and errors in the admin history.

### Required controls

| Control | Behavior |
| --- | --- |
| Roles | Explicit developer/editor/publisher authorization verified by the backend. A parent role in the app must not confer publishing rights. |
| Draft isolation | Ordinary clients can read only the published projection, never drafts, internal notes, audiences, device tokens, or send logs. |
| Editorial metadata | Teaching attribution and optional mentor-review status are recorded. Required practice guidance remains separately reviewed/versioned. |
| Scheduling | Show schedule timezone, publication start, end/expiry, and send time. Publishing and notification sending can occur at different times. |
| Audience | Start with a test audience and all opted-in compatible installations; optional locale/version filtering. “All users” means eligible reachable installations, not guaranteed receipt by every account. |
| Versioning | Every edit records revision, editor, and timestamp. Content edits do not resend a campaign; resend requires a new explicit send operation. |
| Cancellation | Cancel unpublished schedules and not-yet-dispatched recipients. Clearly state that an already delivered notification cannot reliably be recalled. |
| Emergency controls | Pause future campaign sends and hide/unpublish remote feed items, while leaving local japa and the permanent guide usable. |
| Reporting | Distinguish queued, attempted, provider-accepted, failed, and cancelled. Show delivery/open events only when actually available; never equate provider acceptance with user reading. |

Use ordinary positive messaging rather than urgency tricks or streak pressure. Do not silently subscribe users when they sign in with Google or enable Sandhya permissions.

## 4. Data and architecture proposal

Use Firestore as the structured content source, a trusted backend for publishing and dispatch, and FCM for announcement delivery. A bounded client configuration can hold feature-enable/disable values; the content store remains the source for teaching bodies, revisions, and publication lifecycle. A separate Remote Config dependency is not required for the first release.

### 4.1 Proposed records

| Record | Key fields |
| --- | --- |
| Private content draft/revision | Stable content ID, revision, schema version, type, locale, title, summary, body, attribution, optional image, supported action, editor, review status |
| Published content projection | Content/revision ID, supported schema/type, visible fields, start/end times, order/pin, minimum compatible app version, publication status |
| Campaign | Stable campaign ID, content revision, notification title/body, audience rules, schedule/timezone, expiry, status, creator/publisher, request idempotency key |
| Installation registration | Stable installation ID, supported registration identifier/token, optional linked UID, locale, app version, timezone, preference/quiet hours, last refresh, enabled state |
| Dispatch item | Campaign + installation identity, attempt/claim status, retry information, provider result, timestamps; never the source of truth for “read” |
| Local content state | Cached published records, freshness cursor/time, read/dismissed state, locally processed campaign IDs, queued quiet-hours messages |

Suggested paths are private `contentDrafts/{id}` and `campaigns/{id}`, and a client-readable `publishedContent/{id}` projection. Device registrations and dispatch logs require tightly scoped backend/owner access. Exact names are implementation choices; do not add unbounded arrays of revisions, registrations, or recipients to a single user document.

The published feed may expose generic public teachings to supported local-only/signed-out experiences; keep it free of account-specific data. Authenticated reads are also possible if required by the existing navigation. Choose the read contract explicitly and enforce it in rules. Audience filtering is not a substitute for access control if private content is introduced later.

### 4.2 Publishing and Home fetch

- [ ] The backend validates publisher identity, content shape/length, locale, dates, schema, approved image hosts, and allowed in-app destinations before creating the published projection.
- [ ] Scheduling must work without an administrator leaving their browser or the mobile app open. Use an idempotent server job to publish/expire scheduled items and enqueue scheduled campaigns.
- [ ] Keep drafts separate from public projections; do not rely on a client UI filter to hide them. Test the exact feed query against Firestore rules and indexes.
- [ ] Add a domain content repository, data cache/remote implementation, and Home/content-detail UI state. Use paginated/bounded queries and a small initial payload.
- [ ] Render plain text or a deliberately limited supported rich-text model. Do not fetch and execute scripts or arbitrary HTML to obtain “dynamic” behavior.
- [ ] Apply schema/version checks and approved route resolution. Keep essential built-in guide copy, notification permissions, and count rules outside editor-controlled content.
- [ ] Retain useful cached content on fetch failure; respect expiry and process unpublish/revision changes on the next successful refresh. Test fresh install with no cache.

### 4.3 Notification transport and ownership

The existing function sends a notification payload with data, while the service routes received messages through the goal channel. FCM handles notification payloads differently in foreground and background, so an implementation that only changes `onMessageReceived` would be incomplete. [FCM message types](https://firebase.google.com/docs/cloud-messaging/customize-messages/set-message-type).

- [ ] Proposed announcement transport: normal-priority **data messages** with campaign/content IDs, revision, minimal display text, locale, creation/expiry, and validated destination type. This lets the app check local opt-out, quiet hours, expiry, and active-japa state before posting. Accept delayed delivery; the feed is the durable reading surface.
- [ ] Add an explicit announcement message type and `AnnouncementNotifier`; preserve existing family-goal routing. Do not reuse the goal channel or its notification ID range.
- [ ] Keep message-receipt work short and local. Have enough validated text to display without a network fetch; defer content refresh/longer work to the existing appropriate background mechanism.
- [ ] Use a stable campaign ID for durable receive deduplication and notification identity. Claim dispatch work idempotently and retry transient failures. A lost provider acknowledgement can still cause redelivery; suppress it client-side instead of promising exactly-once network delivery.
- [ ] Use installation-level registrations so a second phone does not overwrite the first phone's address. Reconcile registration updates with the installed SDK: the current repository uses legacy FCM tokens; verify the supported API when implementation versions are chosen.
- [ ] Track registration freshness, retire invalid/stale registrations, and re-associate/disable registrations on account switch, sign-out, preference changes, and deletion. Registering for general messages need not force Google sign-in solely for this feature; define installation ownership for that case. [FCM registration management](https://firebase.google.com/docs/cloud-messaging/manage-tokens).
- [ ] Proposed default for general teachings: notify each opted-in installation once, subject to its preferences. Do not claim one notification per human when they opted in on multiple devices.
- [ ] Put provider credentials and sending rights only in the trusted backend. Client users can change their own subscription/preferences, not publish content or dispatch arbitrary campaigns.
- [ ] Keep private family messages on owner/relationship-scoped routing. General broadcast audiences must never carry child goal or account data.

### 4.4 Operational completion

- [ ] Define bounded batches, retry/backoff, expiry, and checks of pause/cancel state before each batch. Provider/network retries must not create another campaign.
- [ ] Make recipient claims consistent with the latest server-known opt-out, quiet hours, timezone, and app compatibility, and recheck locally at display time.
- [ ] Decide whether deferred messages are rescheduled or dropped at expiry; proposed default is to defer within the validity window, then drop the notification while retaining any still-published feed item.
- [ ] Record deployment/runtime dependencies and verify billing/quota requirements before selecting the production dispatch/scheduler implementation. Add budget/usage visibility for operators without embedding operational details in user flows.
- [ ] Update privacy/data-deletion descriptions for installation registrations, content interactions if collected, and notification preferences. Minimal read/dismiss state can remain local; do not add individual reading surveillance as a default.

## 5. Release C stages and dependencies

| Stage | Deliverables | Exit condition |
| --- | --- | --- |
| C1 / W8: publish and display | Protected draft/publish interface, published schema/rules, Home renderer and detail route, cache/fallback, schedule/unpublish/revision | Developer changes supported Home text on a compatible installed app without another APK; offline Home and the permanent guide remain usable. |
| C2 / W9: send messages | Announcement category/opt-in, quiet hours, installation registrations, trusted campaign dispatch, test/send/schedule, tap routing | A test campaign reaches opted-in test devices in supported states and opens the right teaching; opt-out does not affect Sandhya. |
| C3 / W10: production readiness | Duplicate/retry handling, audience/expiry/cancel validation, rollback/pause, report semantics, staging and real-device checks | Complete one staged publish/send/revise/unpublish cycle and document actual delivery limits before general rollout. |

C1 can be developed independently of Google sign-in and the count ledger, provided Home/navigation integration is agreed. C2 needs its own protected developer authorization and a clear registration-ownership contract, not necessarily the user's new Google provider. A Firebase-console test send can help validate device transport, but it does not replace the required integrated publishing, preferences, and campaign workflow.

## 6. Acceptance and test checklist

| Test | Expected result |
| --- | --- |
| Publish a teaching | Current compatible app shows it after refresh, with no APK update; unpublished draft never appears. |
| Edit/pin/schedule/expire | Eligible cards and order update; scheduled content does not appear early and expired content is hidden. |
| Publish without send | Home updates and no notification campaign is created. |
| Test before general send | Only the test audience receives the test; general recipients remain untouched. |
| Permissions/preferences | Sandhya access does not subscribe to teachings; disabling teachings preserves Sandhya reminders and Home reading. |
| Quiet hours/active japa | Announcement is deferred or expires; it does not interrupt mental japa or suppress scheduled Sandhya. |
| App foreground/background/process absent | At most one local notification per campaign after receipt; correct channel and full-content destination. Record observed delays. |
| Retry/duplicate/timeout | Stable campaign/dispatch identities and receive deduplication prevent repeated user-facing announcements. |
| Second phone/token change/account switch | Correct installations remain reachable; stale/invalid registrations are retired without cross-account private data. |
| Unpublish after send | Online tap reports unavailable or opens current content as appropriate; offline limitations are explicit. |
| Malformed/unknown/old-client content | Unsupported data is ignored or shown through a safe fallback; no crash or executable content. |
| Offline with/without cache | Valid cached or bundled teaching renders; counters, local reminders, and the permanent guide still work. |
| Unauthorized operations | Normal app users, including parents, cannot read drafts/registrations/send logs or publish/send. |
| Delivery reporting | Provider acceptance is distinct from actual delivery/opening; unavailable measurements are not shown as success. |
| Pause/cancel/rollback | Future or remaining work stops, prior approved content can be restored, and delivered messages are not falsely reported recalled. |
| MF-03 continuity | New remote content never hides permanent guidance or changes its explicit acknowledgement state. |

No production notifications are sent as part of preparing this plan. The first end-to-end implementation validation should use test devices and staging content.
