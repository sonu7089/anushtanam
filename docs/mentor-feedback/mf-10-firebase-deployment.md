# MF-10 Firebase deployment

## Notification-only update, 11 September 2026

Deployed the publisher-only standalone notification operation, its dispatcher branch, and the optional boolean `notificationOnly` registration field to `anushtanam-shastralife`. Firebase reported successful updates for `adminMessages` and `tickMessages` and released the rules. No new collection or index is required. The 30-minute schedule remains in place. No live campaign or notification was created or sent during this update.

The app must be updated and reopened before its installation becomes eligible for nudges. Message-linked notifications continue to support older registrations. See [implementation and verification](notification-only-implementation.md) and [the website sending flow](mf-10-website-guide.md#notification-only-nudges). Deployment output is in `backend/firebase/notification-only-deploy.log`.

10 September 2026. Project: `anushtanam-shastralife`, matching the Android app configuration. Firestore location: `asia-south1`.

## Database update

The live message query initially failed with `permission-denied`. The live rules did not yet include MF-10, and there were no composite indexes. Comparing the live rules with this repository confirmed that only the message permissions and their explanatory comments were added. Existing account, backup, and family rules were preserved.

Deployed the repository's Firestore rules and both indexes with an explicit project ID:

```text
firebase deploy --project anushtanam-shastralife --only firestore:rules,firestore:indexes --non-interactive
```

Firebase accepted the deployment. The released rules match the local file. The new indexes are for `publishedContent` (`pinned` descending, `publishedAt` descending) and `campaigns` (`state` ascending, `sendAt` ascending).

Both indexes reached `READY`. The same unauthenticated, server-only query used by the app (pinned descending, publication date descending, 30 records) now succeeds and returns zero records. The list should show its ordinary empty state after refresh because no content has been published. No APK update is needed for this database fix.

## Blaze and message functions

The first attempt was blocked because billing was disabled. The owner enabled Blaze, and the agent then deployed `adminMessages`, `tickMessages`, and `deleteMessageInstallations`. All three functions are now `ACTIVE` on Node.js 22 in `us-central1`. The deletion trigger receives Firestore events from `asia-south1`.

The first deletion-trigger attempt encountered Google's initial Eventarc permission propagation delay. The required service-agent role was already present; retrying that function completed successfully without broadening permissions.

The live scheduler is `ENABLED` with `every 30 minutes`. This is 48 runs per day, or 1,440 in a 30-day month. Scheduled publication and sending normally wait up to 30 minutes for the next run; provider delays, retries, or large batches can take longer. Manual app refresh and local japa deferral are unchanged.

Deployment command for future updates:

```text
firebase deploy --project anushtanam-shastralife --only functions:adminMessages,functions:tickMessages,functions:deleteMessageInstallations --non-interactive --force
```

The Functions dependency lockfile is now included. The deployment runtime is Node.js 22, supported by [Firebase's runtime configuration](https://firebase.google.com/docs/functions/manage-functions#set_nodejs_version). The existing family-goal function was not included in the deployment request.

Firebase configured the `gcf-artifacts` repository to remove build images older than one day. This cleanup policy was verified through the API. It limits storage buildup and does not remove the running functions. No message was published or notification intentionally sent as part of deployment verification.

The separate website still needs its account's publisher claim and authorised domain when it is connected. No account was granted publishing access during this deployment.

## Validation

- All 40 local Auth/Firestore emulator tests passed, including the message access rules.
- All nine standalone message policy and workflow tests passed.
- The installed function package loads all four expected exports.
- All three message functions are active, and the live scheduler is enabled at the requested 30-minute interval. An unauthenticated `listContent` request returns HTTP 403 with `PERMISSION_DENIED`, confirming that private publishing data requires authorization. Live results are in `mf10-blaze-verification.log`.
- Live rules match the repository, both indexes are ready, and the exact public feed query succeeds. Results are in `mf10-live-verification.log` and `mf10-feed-verification.log`.
- Logs are in `backend/firebase/`: `mf10-firestore-deploy.log`, `mf10-functions-deploy.log`, `mf10-deployment-rules-tests.log`, and `mf10-deployment-policy-tests.log`.
- Final Blaze deployment logs: `mf10-blaze-deploy.log` and `mf10-blaze-cleanup-deploy.log`. The first records the initial Eventarc delay; the second confirms successful completion of the last function.

### Quiet live checks after activation

At the owner's request, repeated live checks confirmed billing enabled, all three functions active, the 30-minute scheduler enabled, and a successful public message query returning zero records. Anonymous draft reads are denied, and the private callable rejects unsigned requests with HTTP 403 / `PERMISSION_DENIED`.

Before testing a scheduler execution, checked that `campaigns`, `messagePublicationJobs`, and `publishedContent` were all empty. Then requested one run of the existing scheduler job. The recorded attempt at `2026-09-10T17:09:12.135577Z` completed with a successful status; the job remained enabled with `every 30 minutes`. No test content, user records, or notifications were created.

The initial one-hour runtime error query found no errors. A later log query hit the Firebase CLI client's shared Cloud Logging read quota, so completion was verified directly through Cloud Scheduler's job status. Phone delivery and the future website publishing flow were not exercised by these quiet checks.

Logs: `mf10-live-smoke.log`, `mf10-live-smoke-run.log`, and `mf10-scheduler-test-result.log` in `backend/firebase/`.

### Owner-requested phone test

The owner subsequently requested a message and notification on their phone running the latest APK. Identified the owner's recently refreshed, enabled registration and marked it as a test phone. Published the original note **A small note for you** with a 24-hour publication window and created a test-audience campaign with a one-hour send window. The trusted local CLI used the same content validation and draft/revision/publication/campaign records as the backend, with an audit record identifying the CLI operator. This did not assign a website publisher claim or exercise the website sign-in flow.

Confirmed there were no other test phones or queued campaigns before requesting a one-off run of the deployed scheduler. The regular 30-minute schedule remained unchanged. The campaign completed with one FCM acceptance, zero failures, and one other registration skipped. The app's public read of the test message was also verified. The owner confirmed receiving both the notification and the message on their physical phone while the app was open. This confirms that foreground test path; cold-start taps, idle-phone behaviour, and live japa deferral remain separate checks.

Logs: `mf10-phone-test-manifest.log`, `mf10-phone-test-send.log`, and `mf10-phone-test-result.log` in `backend/firebase/`.
