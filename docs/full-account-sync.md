# Full account backup

Signed-in accounts now back up preferences, profile display choices, unfinished count drafts,
and full evaluation results, alongside the existing practice ledger, goals, and Sandhya logs.

## Stored data

- `users/{uid}/accountRecords/preference_*`: theme, script display, explicit sensitivity,
  Advanced/Vedic mode, reminder switches, selected region/coordinates/timezone, and each
  Sandhya minimum. Values are account-specific. OS notification permission remains device-specific.
- `users/{uid}/accountRecords/profile`: local display name, avatar index, and photo visibility.
  Existing account documents still own authentication role and Google photo identity.
- `users/{uid}/accountRecords/draft_*`: input, period/date/timezone, original quantity/revision,
  mode, and edit time. Saved/discarded drafts have permanent tombstones. Concurrent draft
  records remain in the account; the local editor shows the most recently edited open draft.
- `users/{uid}/practiceRecords/session_*`: new optional `assessmentJson` preserves ordered
  word feedback, acoustic scores/pacing, per-line scores/coverage, missing/extra words,
  recognition confidence/alternatives, diagnostic flags/SNR, swara results, and effective
  evaluation parameters. Existing scalar scores, duration, and transcription are unchanged.

Account records use the existing transactional Room outbox and change feed. Local copies
live under the `account:record:` namespace in `backup_queue_state`; settings edits commit
the record and upload together. Writes are ordered by edit time and operation identity;
terminal draft markers win over late edits. Migration seeds have timestamp zero and only
fill missing remote records. Legacy global device preferences are claimed once, so another
account does not inherit them. Local-profile records are imported only with that profile.

Room schema 18 adds `assessmentJson` without rewriting old assessments. Remote draft
base revisions are rebased to the local count only when their base quantities still agree;
otherwise the editor retains its existing conflict protection.

Full speech details no longer expire at 365 days. The maintenance function still processes
account deletion. Previously erased speech and previously unrecorded evaluation fields
cannot be recovered. The compact progress summary still keeps 60 date keys; complete
historical sessions remain in `practiceRecords`, so this is not a history-retention limit.

Raw audio is not currently captured by the user practice flow, and this change does not
start recording it. New product analytics (screen visits, abandoned attempts, crashes)
are outside account backup and are not introduced. Installation IDs, credentials, OS
permissions, reminder delivery deduplication, and retry bookkeeping stay device-specific.
Unattributed legacy diagnostic files cannot safely be assigned to an account; diagnostic
metrics from new evaluations are included in their owned session snapshots.

## Release order

1. Deploy the updated Firestore rules and privacy functions before distributing the app.
   Otherwise account-record writes will be rejected and the old hourly function will
   continue redacting speech. Pending writes stay queued until the backend is ready.
2. Publish the updated privacy-policy page (settings include chosen coordinates).
3. Release the app with the v17-to-v18 database migration. Use Settings backup status to
   confirm an initial sync completes and test restoration on a second installation.

## Production deployment — 24 September 2026

At the owner's request, Firebase CLI deployed `firestore:rules`,
`functions:eraseRequestedAccount`, and `functions:maintainPrivacy` to
`anushtanam-shastralife`. Both Node.js 22 functions were verified `ACTIVE` in
`us-central1`, with source hash `2ff0cc3cbed24dd2a075330cf3b76083057caa34`.
The active Firestore ruleset is
`projects/anushtanam-shastralife/rulesets/3b3ac008-b2ea-4906-b31a-3e274c19cc59`;
its contents were fetched and verified byte-for-byte against the tested local rules.
Existing function parameter `ENFORCE_ADMIN_APP_CHECK=false` was preserved; the temporary
dotenv file used for deployment was removed afterward. Unrelated functions were not redeployed.

The previously empty Firebase Hosting site now publishes:

- https://anushtanam-shastralife.web.app/privacy-policy.html
- https://anushtanam-shastralife.web.app/delete-account.html

Both returned HTTP 200 and matched the maintained source documents exactly, including
the 24 September 2026 revision date. The site root also serves the privacy policy.
`scripts/build-privacy-site.cjs` prepares the Hosting files from `playstore-assets`, the
same sources used by Android's `bundlePrivacyDocuments` task. Future publication uses
`firebase deploy --only hosting --project anushtanam-shastralife` from `backend/firebase`.

Backend deployment and web publication are complete. Distribution of a new Android app
release and any Play Console listing URL updates are separate release steps.

## Verification

- JVM tests cover rich result serialization, old session compatibility, preference
  validation, large integer counts, deterministic conflicts, and terminal drafts.
- Firebase emulator tests cover owner-only access, change-feed atomicity, stale writes,
  old speech preservation, and deletion of nested account records.
- Android instrumentation tests cover transactional rollback and record replay, draft
  restoration, account isolation, and migrations from every supported database version.
  Run these on a connected device/emulator; compilation alone does not execute them.
