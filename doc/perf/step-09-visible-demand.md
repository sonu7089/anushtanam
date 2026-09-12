# Step 09 — UI data follows visible demand

Implemented and locally verified on 12 September 2026. Resolves UI-03/04/05/08/09, RW-02/03/05/13, and the UI-lifecycle part of POLL-06. Verification results are summarized in [step-09-results.json](step-09-results.json).

## Destination and section ownership

Presentation state is collected with `collectAsStateWithLifecycle`. Home and Progress use subscription-owned state producers; Family, Targets, Purashcharana and both child-progress producers use [observeWhileSubscribed](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/VisibleDemand.kt). Leaving a destination or stopping its lifecycle cancels these screen reads, even when its ViewModel remains retained. Account changes cancel replaced work, and profile/account identity scopes Progress caches and backup reviews. Profile-name changes do not restart their data queries.

Screen-only reflection, Messages, QR and reminder-settings timers follow the STARTED lifecycle. The existing process-owned account/child-goal producer, notification baselines, durable save jobs, backup coordinator and alarm delivery keep their existing ownership. Sandhya's existing subscription grace period remains five seconds. Replacing visible polling with deadlines and consolidating reminder scheduling remain step 11.

[Home](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt) uses separately keyed lazy items. Its greeting and navigation render with independent dashboard, japa and vow loading/error/retry states. An unresolved japa total is not displayed as zero, and a failed vow lookup is not displayed as an invitation to enroll. The dashboard use case no longer observes children or computes their analytics. Today's verified audio count and streak use a compact per-day projection instead of complete session entities.

## Progress queries and paging

[ProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressViewModel.kt) owns selected-tab demand, account-scoped cached content, retries and append requests. Tab controls remain available during slow reads and errors. A switched tab cancels its predecessor, and each tab's scroll state is saved while another tab is selected.

| Selected tab | Necessary local read | Work that remains inactive |
| --- | --- | --- |
| Overview, valid cloud summary | Performed-japa projection | Local assessment fallback, charts, history |
| Overview, no usable cloud summary | One totals row, correct-day counts, performed-japa projection | Charts and history |
| Daily chart | Performed-japa projection; seven date labels generated directly | Audio analytics, cloud summary and history |
| Accuracy | One seven-day SQL aggregate shared by headline accuracy and all three axes | Overview, japa and history |
| History | One page of session metadata | Overview, charts, japa and cloud summary |

Cloud overview changes do not restart the japa producer. Exact performed counts continue to use BigInteger. The [japa read projection](../../core/data/src/main/java/com/mantrasiddhi/data/local/dao/JapaDao.kt) returns only dates and quantities, including accepted NORMAL-mode legacy audio without a corresponding ledger entry. It does not read transcription, word scores or complete ledger entities. It preserves the prior legacy fallback and performs exact aggregation off Main; it does not introduce overflow-prone SQL SUM over Long quantities. Lifetime count work still scales with the number of performed entries, but only the small fields needed to calculate it are materialized.

History uses `(createdAt DESC, id DESC)` cursors and reads at most 31 metadata rows: 30 displayed rows and one lookahead. Load more fetches only the next requested page. Equal timestamps do not cause overlaps or omissions. Append failures retain rows and cursor for retry; cached pages survive tab revisits. When the observed first page changes, the loaded list restarts at the new first page, avoiding an inconsistent mixture of cursor generations. The query uses the existing step-08 index; no schema migration was added.

## Backup and Messages

[Backup status](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/BackupViewModel.kt) observes only state and pending/conflict/quarantine counts. Opening review starts one joined query for the next unresolved conflict and its account-owned entry. Closing review cancels it. Missing or malformed review data shows a retryable review error. Conflict resolution is bound to the UID that produced the displayed choice. Full conflict lists and ledger lists are no longer constructed for Settings' badge/status.

[Messages](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingRepository.kt) emits a valid memory/Firestore cache page or detail before beginning the server refresh. Disk-cache reads and decoding run off Main, with a bounded initial cache wait. A cancelled refresh retains the cached page for reopening during the shared cooldown. Successful server refreshes authoritatively remove deleted, unpublished or expired content; offline refreshes retain readable cached content, including already appended pages. Refresh and append states/errors are separate. Failed appends retain their cursor; superseded detail requests and hidden-screen requests are cancelled and guarded against late publication. The feed remains public published content rather than account-private cached data.

## Verification

- All 609 unit tests pass (364 domain, 138 data, 107 app). Unit suites cover selected-tab query counts, duplicate selection and profile-metadata updates, shared japa during cloud updates, account replacement during a suspended read, bounded paging with tied timestamps, append failure/retry, cached Messages before a suspended refresh, authoritative deletion, retained appended pages, superseded details, Home's independent loading and backup-review demand. Existing account, child-selection, goal and counting regressions also pass.
- Physical Pixel 7 Pro Room tests use isolated databases at 0, 1,000 and 10,000 sessions. They verify SQL aggregates, seven-day bounds, 31-row history pages, cursor overlap/continuity, account isolation, absence of heavy session columns, legacy japa fallback and one-entry conflict review. The existing japa accounting/migration tests pass alongside these: 13 tests total.
- Physical Compose tests verify selected-tab navigation, load-more interaction, stopped/resumed lifecycle, cancellation with retained ViewModels, independent Home navigation and lazy below-fold sections, Messages reading/back/refresh controls, backup review, reflection updates and reminder-settings UI. There are 25 distinct passing tests (20 in the first complete set; the additional set repeats three Progress tests and adds Home plus four reminder tests).
- Debug and optimized benchmark APK builds and Android lint pass. Lint reports no errors. The debug APK is installed on the connected Pixel without clearing its data.

The first Compose attempt ran while the phone was dozing behind the lock screen and was discarded. After waking the phone, the History test exposed an incorrect reused Messages button label; a dedicated “Load more” label fixed it. Final runs pass.

## Measurement limits and remaining work

The Room samples are debug-device query checks, not release tap-to-content or frame-time benchmarks. Recorded overview timings include the totals/day reads only; they exclude japa, cloud and rendering. They are single samples and do not establish a latency improvement. The decisive measured work bounds are zero full session-entity reads for these Home/Progress paths, at most seven score-day rows, 31 history rows per page, one conflict review row, and no active screen reads after the tested demand ends.

The initial audit's eager pipeline had five independent full-session mappings plus the raw japa fallback. It is retained as historical evidence, not a like-for-like timing baseline for this implementation. Complete repeated startup/navigation/idle traces remain step 13. Image resizing/startup work remains step 10; clock/date deadline updates and alarm consolidation remain step 11. No Firebase rules, production deployment or cloud data were changed by step 09. The earlier production rules and two-phone release requirements still apply.
