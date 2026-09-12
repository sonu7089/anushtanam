# Step 03 — identities and counting contracts

Implemented and verified locally on 12 September 2026. Room advances from v14 to v15. This resolves DB-06 and DB-11's local identity, assessment and restore-admission gaps.

## Repair before constraints

[IdentityRepairMigration](../../core/data/src/main/java/com/mantrasiddhi/data/local/IdentityRepairMigration.kt) runs after the existing contiguous migration chain. Valid unique IDs are preserved. Original changed/removed rows remain as JSON in `identity_repair_archive`, keyed by table and original row ID with the original owner and reason.

- **Goals:** a definition matching the persisted backup fingerprint retains the existing cloud ID. Without that evidence, the deterministic fallback is active first, newest start first, then lowest local row ID. Other colliding rows receive IDs derived from their definition and an ordinal, independent of local profile/row numbers. A mantra that distinguishes an allocation moves it to the corresponding repaired goal. Ambiguous shared allocations stay on the canonical identity; other historical goal rows retain their recorded display as a baseline and close at their start, preventing new or same-day retroactive credit.
- **Sessions and credit:** identical assessments sharing a stable ID merge onto one assessment, redirecting local entry references. Differing assessments remain as separate history under repaired IDs. For multiple accepted entries referencing one session, the earliest entry (then lexical entry ID) remains in the live ledger. Duplicate entries and invalid local session references are archived. Affected goal projections are recomputed with BigInteger arithmetic before capping.
- **Orphans:** missing profile references are archived diagnostically and retain their original owner. The profile auto-increment sequence advances beyond referenced orphan IDs so a new profile cannot inherit that dormant history. No profile is invented, reassigned or cascade-deleted.
- **Eventual associations:** `sessionStableId` on entries and `acceptedEntryId` on sessions retain both directions of an association. Existing valid links are backfilled. An unresolved v14 session claim is recovered from its persisted fingerprint when it does not conflict with existing data.

Only after repair, unique indexes enforce `(userId,stableId)` on targets, vows and sessions, `(userId,sessionId)` and `(userId,sessionStableId)` on entries, and `(userId,acceptedEntryId)` on sessions. Nullable association keys allow multiple unlinked historical/manual records. These are correctness constraints; this step claims no measured query speedup.

## Write and restore contracts

[RecitationRepositoryImpl](../../core/data/src/main/java/com/mantrasiddhi/data/repository/RecitationRepositoryImpl.kt) saves immutable assessments. The domain model retains the stable session ID through entity conversion. An identical retry, including concurrent retries, returns the original local ID without another ledger entry or backup enqueue. Reusing an ID to change owner, mantra, eligibility, scores or other assessment fields fails. Session and target inserts use ABORT instead of REPLACE. Association metadata can fill an initially missing link without changing the assessment. A future assessment-edit feature must define a separate transactional correction protocol.

[JapaDao](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/JapaDao.kt) checks known session ownership, mantra, timestamp, NORMAL/correct eligibility, one-unit audio quantity, source/period compatibility, duplicate associations and original allocation identity before writing. Manual entries cannot credit a known parent-assigned target. Repository saves check local profile existence; target updates preserve identity and ownership. Raw SQL remains a trusted internal/migration interface, not an authorization boundary.

[BackupRecordRestorer](../../core/data/src/main/java/com/mantrasiddhi/data/sync/BackupRecordRestorer.kt) checks the requested Firebase owner against the local profile and validates references before admission. Conflicting assessments, duplicate claims, cross-owner entry IDs, changed allocations and mantra disagreements enter the existing account-scoped quarantine, allowing unrelated records and the page checkpoint to commit. Stale revisions cannot undo newer local allocation repair; quantity corrections retain original allocations.

Goal IDs and session stable IDs are intentionally eventual, so immediate foreign keys were not added. Entries can arrive before sessions/goals; sessions can arrive before entries, including legacy entries without `sessionStableId`. The second side validates against the first and links transactionally. Contradictory late records are quarantined. Restore never guesses another profile's local numeric IDs.

## Verification

| Check | Result |
| --- | --- |
| Domain/data/app unit suites | 548 passed: 360 domain, 109 data, 79 app; zero failures/errors/skips |
| Physical-device Room/contract suites | 50 passed on Pixel 7 Pro, including nine new identity/contract tests |
| Supported migration starts | Every version 1–14 reaches v15 with mixed histories; schema validation plus two production-factory reopens per starting version |
| Firebase recovery on the physical phone | 3 passed using isolated loopback Firestore/Auth emulators and two independent Room databases |
| Debug app and instrumentation APKs | Built successfully |
| App lint | No errors; 14 warnings and 3 hints |
| Patch whitespace | `git diff --check` passed |

[IdentityMigrationTest](../../core/data/src/androidTest/java/com/mantrasiddhi/data/local/IdentityMigrationTest.kt) covers repair before uniqueness, duplicate/contradictory assessments, invalid local associations, orphan preservation and ID reuse prevention, unresolved v14 claims, cloud-fingerprint selection, mantra-based allocation repair and identical replacement IDs across different local profile/row numbers. Exported schemas exist for v4 onward; v1–v3 fixtures reconstruct the documented DDL differences from v4 and execute the real production chain. They are not recovered historical APK databases.

[IdentityContractsTest](../../core/data/src/androidTest/java/com/mantrasiddhi/data/local/IdentityContractsTest.kt) covers concurrent save retries, eligibility edits in both directions, cross-owner/mantra changes, uniqueness independent of DAO guards, both restore orders across reopening for current and legacy payloads, conflicting claims and continued admission of unrelated history. Existing accounting fixtures now give distinct assessments distinct IDs and persist PRACTICE sessions before requesting credit.

The instrumentation schema asset task now depends on Room schema generation, fixing a verification race that could package the previous schema export in a one-pass build.

Retained evidence: `build/perf-03-verification.log`, `build/perf-03-final-verification.log`, `build/perf-03-device-correctness.log` (`OK (50 tests)`), `build/perf-03-device-cloud.log` (`OK (3 tests)`) and standard Gradle XML/lint reports. Firebase used `demo-anushtanam` on loopback ports 48080/49099 with a workspace copy of the current rules. Only the separate data instrumentation package was installed; tests used disposable databases. The app APK is `frontend/android/build/outputs/apk/debug/app-debug.apk`.

## Compatibility and limits

The v1 wire format remains compatible: new local columns represent existing optional wire associations. No Firestore rules or production records were deployed/rewritten. The installed main application's database was not migrated during testing; installing the new app runs v15 on first database open. Existing outbox rows and restore checkpoints remain intact.

The repair cannot reconstruct distinctions already erased by a previous cloud overwrite. A fingerprint records the last locally queued definition, not proof of what every device saw. Without disambiguating evidence, the canonical/frozen-history rule retains original snapshots instead of inventing an allocation. The archive is local diagnostic history, excluded from backup and performed totals, with no automatic restore or end-user editing UI. Incompatible old cloud records can remain quarantined and produce the existing incomplete-count message.

These checks establish supported local contracts, not a production-data audit or a two-physical-device rollout result. Exact compact cloud-summary/assigned-goal recovery remains step 06; unrelated legacy corruption remains outside this repair policy.
