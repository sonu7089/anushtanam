# Cloud Functions review and implementation — 2026-09-14

Reviewed `03-cloud-functions.md` against the current working tree, preserving the
existing authentication and Firestore remediation changes. Changes are in source;
no production deployment, role changes, or console changes were performed.

## Review corrections

- F-01's payload-size concern is valid. The claimed infinite retry loop is not:
  event-trigger retries are disabled by default and were not enabled for this trigger.
  See [Firebase retry behavior](https://firebase.google.com/docs/functions/retries).
- F-02's custom-claim authorization is sound. App Check already has an
  `ENFORCE_ADMIN_APP_CHECK` rollout parameter, defaulting to false. Three instances
  do not imply only three concurrent requests; this is not proof of a trivial DoS.
- F-03 is valid: untagged runtime errors were returned as validation messages.
- F-04 is valid. `campaignDetails` does not scan test devices in the current code.
- F-05 misstates delivery direction. Registering someone else's token sends to that
  device; it does not let the registering account receive that device's messages.
  Firebase Auth and App Check alone do not prove FCM token ownership. A real ownership
  protocol needs a device-delivered challenge and acknowledgement. No purported
  token/session ownership check was added. Misregistration remains accepted residual
  risk for broadcast content.
- The campaign dispatcher is an internal helper, not a separately exported function.
  The dispatcher also cannot guarantee exactly-once FCM delivery: a crash after send
  acceptance and before persisting the result can cause a duplicate on retry.

## Implemented

- Goal pushes use fixed text in both notification and data payloads. Reward text and
  practice counts are never interpolated, including for legacy 200,000-character
  rewards. The trigger explicitly disables retries, absorbs permanently invalid
  registration errors, and surfaces unexpected failures. Logs omit child IDs and
  token values. Invalid tokens are not deleted here, avoiding a race with token refresh.
- Retained the existing reward field limit. Reducing the shared goal validator to
  200 characters would reject progress writes on legacy goals; the fixed notification
  removes the push exposure without a data migration. A separate UI/storage limit
  would need coordinated client validation and legacy handling. Android's existing
  local `GoalNotifier.showGoalSet` path is unchanged; this is a Cloud Functions fix,
  not closure of all local lock-screen disclosure paths.
- Added `PolicyError` and explicit timezone validation. Only tagged validation errors
  and deliberately constructed `HttpsError` responses reach callers. Unexpected
  errors return a generic internal response and log an error type server-side.
- Added a transactional token bucket in `messageRateLimits/{sha256(uid)}` after
  authorization and before operation dispatch. Capacity is 60 credits; refill is one
  credit per second. Audience estimates cost ten credits, other operations one.
  Limits apply across instances and operations. Existing default-deny rules block
  client access to these documents. Rejected requests still incur function and
  transaction reads; this bounds downstream work, not all request costs.
- Removed unbounded test-device collection scans. Membership uses point reads only
  for the current page/sample (50/1,000 documents), and general-audience estimates
  and dispatch skip membership reads. This avoids an in-memory cache that could keep
  removed test phones eligible across invocations.
- Added `backend/firebase/scripts/message-role.cjs`: inspect, grant, or revoke a role
  using application-default credentials and an explicit project, user and operator.
  Grants require an enabled account with Firebase MFA enrolled. Other claims are
  preserved; changes log intent/completion to `messageAudit` and revoke refresh tokens.
  Dry-run is the default. Auth and Firestore are not atomic: reconcile any audit entry
  stuck in `requested` against Auth before retrying. Serialize role changes to avoid
  overwriting concurrent custom-claim updates.

## Operations still required

Run from `backend/firebase`, with dependencies installed in `functions`:

```text
node scripts/message-role.cjs PROJECT_ID UID inspect OPERATOR
node scripts/message-role.cjs PROJECT_ID UID editor OPERATOR
node scripts/message-role.cjs PROJECT_ID UID publisher OPERATOR --apply
node scripts/message-role.cjs PROJECT_ID UID none OPERATOR --apply
```

The operator label is a supplied audit annotation, not an authenticated identity;
restrict script execution through IAM and retain Cloud Audit Logs for attribution.
Review existing privileged accounts and MFA enrollment separately. The script guards
new grants; it does not force existing admin sessions to use MFA. The external admin
site still needs review, MFA sign-in integration, and App Check provider setup. Keep
the staged rollout from `01-implementation.md`: verify legitimate App Check traffic,
then set `ENFORCE_ADMIN_APP_CHECK=true` and deploy `adminMessages`.

Existing ID tokens may retain old privileges for up to one hour after role revocation;
refresh-token revocation does not immediately invalidate them in this callable.
See [Firebase session management](https://firebase.google.com/docs/auth/admin/manage-sessions).
Export admin audit events to restricted immutable storage and review role holders
periodically. Production IAM, accounts, audit exports and enforcement were not inspected.

Deploy the changed functions through the normal release process. No Firestore rules
or Android changes were added by this review. The rate-limit collection requires no
index or TTL; it keeps one small document per admin who uses the API.

## Verification

24 tests passed across the following suites, using isolated handler/provider mocks:

```text
node --test backend/firebase/tests/messages-policy.test.cjs backend/firebase/tests/messages-workflow.test.cjs backend/firebase/tests/security-03.test.cjs
```

Coverage includes existing campaign workflows, role separation, error masking,
bucket exhaustion/refill, denial before bucket creation, oversized Unicode rewards,
missing/invalid tokens, unexpected delivery errors, and role dry-run/MFA/revocation.
The script also passes Node syntax checking. These are not live Firebase, IAM,
App Check, MFA sign-in, or multi-instance transaction integration tests.
