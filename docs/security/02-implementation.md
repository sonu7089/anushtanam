# Firestore rules review and implementation — 2026-09-13

Reviewed `02-firestore-security-rules.md` against the current client, rules and
Functions. Firestore rules were deployed to `anushtanam-shastralife` on 2026-09-13;
see the deployment record below.

| Finding | Review and resulting change |
|---|---|
| R-01 | Applies. Both mirrors now have allowed-field lists, types, count constraints and completion consistency. Identity fields introduced after the legacy schema remain optional. Vow deletion is denied; withdrawal uses `isActive: false`. Owner deletion of a self-target remains explicit because `TARGET_CLEAR` depends on it; historical goals are stored separately. |
| R-02 | Applies to schema validation. Full summaries now require v2, typed nonnegative aggregates, score/count consistency, a maximum of 60 daily keys and monotonic revisions. Client deletion is denied. The report's create-only-v2 recommendation would break the existing bootstrap: a new document may instead contain **only** the v1/revision-0 barrier, and a legacy document may add **only** those barrier fields before the canonical repair. Repair diagnostic fields are validated too. Deleting an unversioned document did not itself bypass the existing permanent v2 cutover; the report overstates that particular replay claim. |
| R-03 | Applies. Profile creates and updates validate known fields, name length (100), HTTPS photo URLs (2048), photo source and bounded checkpoint maps. Role, creation time and legacy link code cannot change. FCM tokens move to owner-only `users/{uid}/private/messaging`; Android registration and the goal trigger use that path. Linked reads of profiles still containing a legacy token fail closed until migration removes it. |
| R-04 | Child-code registration is already denied by the authentication remediation. New profiles cannot declare a link code and existing profiles cannot add/change/remove one. There is no reason to add a new code-allocation Function to the retired flow. |
| R-05 | Already addressed: `childCodes` reads/writes and unsolicited PENDING link creation are denied; current linking requires a child-issued QR invitation. Existing family tests cover this. |
| R-06 | Public generic teachings remain public by design. Firestore App Check enforcement and a billing alert are deployment/console work, still outstanding. |
| R-07 | QR redemption, canonical record/feed writes and atomic session-summary writes pass emulator tests with the current rules. This demonstrates that these paths fit the enforced limits, **not** a measured lookup budget or headroom guarantee. Repeated accesses may be cached, so the report's source-call tally is not an exact billed-read count. |
| R-08 | Added deny-all `storage.rules` and the Storage entry in `firebase.json`. Deployment confirmed Firebase Storage is not provisioned; no bucket was created. |

## Remaining limits

The dynamic `daily` and `syncCheckpoints` maps have key-count caps (60 and 100),
not validation of every arbitrary nested value or a serialized-byte cap. Rules have
no loop or generic all-members type validator. A schema change to independently
validated day/checkpoint documents or trusted server projections would be needed
for comprehensive nested validation. Do not interpret this change as making
client-authored statistics trustworthy or closing all document-size abuse.
Existing malformed profiles may need administrative cleanup before they can be
updated under the stricter schema. The 100-stream checkpoint ceiling also needs
administrative compaction if an account ever reaches it.

Firebase references: [field validation and document-level reads](https://firebase.google.com/docs/firestore/security/rules-fields),
[access-call limits and caching](https://firebase.google.com/docs/firestore/security/rules-conditions).

## Rollout

1. Review existing profile/mirror data against the new schema and confirm Storage is
   unused. Old clients that write profile tokens or unversioned summaries are incompatible
   with these rules; coordinate the Android release and backend cutover.
2. From `backend/firebase`, with application-default administrative credentials,
   run `node scripts/migrate-private-tokens.cjs PROJECT_ID` to inventory legacy tokens.
   Inspect the counts, then rerun with `--apply` for the chosen project. Each move is
   transactional, removes the public copy, preserves an already-valid private token,
   and is restartable. No token values are logged. Null/invalid old tokens are removed;
   their devices register fresh tokens on sign-in/refresh.
3. Coordinate token migration, the updated `onGoalCreated` trigger, Firestore/Storage
   rules and the Android release. Rerun the migration after legacy writers are disabled;
   require `found: 0`. Until migration, linked profile reads fail closed; until private
   registration/migration, the updated trigger skips users without private tokens.
4. Deploy the reviewed rules and trigger using the normal release procedure. Register
   and verify App Check clients before enabling Firestore enforcement, and configure
   billing alerts. These console controls are not established by committing rules.

## Verification

- **65 passed, zero failures/skips** in the Firestore/Auth emulator suite plus database rules regression checks, including
  malformed create/update rejection, legitimate mirror clearing/withdrawal, bootstrap
  barriers, profile immutability, linked-token isolation, migration dry-run and replay.
- `:core:data:compileDebugKotlin --offline` passed against the installed Android SDK.
- JavaScript syntax checks and `git diff --check` passed.
- No physical-device notification delivery, Storage
  emulator evaluation, or quantitative lookup-headroom measurement was performed.


## Deployment record — 2026-09-13

The user approved deployment and token migration for the testing-phase app.

- Migrated 14 legacy token fields across 15 profiles. A subsequent read-only scan
  returned `found: 0`. The migration used the signed-in Firebase CLI account via
  `--firebase-cli`; default application credentials did not have access.
- Firestore rules compiled and were released to `cloud.firestore`.
- `onGoalCreated` (Node.js 22, second generation, `us-central1`) was created
  successfully; Firebase CLI reported `Deploy complete!`. The live function listing
  was retrieved successfully after deployment.
- Storage deployment confirmed that Firebase Storage is not set up. The CLI enabled
  the Firebase Storage API during preparation, but no bucket was provisioned and
  no Storage rules were released. Deny-all rules remain ready in source.
- The function deployment required `ENFORCE_ADMIN_APP_CHECK=false`, its source
  default, in a temporary project dotenv file. Only `onGoalCreated` was selected;
  `adminMessages` and the other functions were not redeployed.
- The Android changes remain in source; this operation does not distribute an APK.
