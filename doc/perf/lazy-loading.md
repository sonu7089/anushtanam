# Lazy and progressive loading

Proposal added 12 September 2026. This extends the [UI performance audit](README.md); no application changes are implemented here.

The app should open the requested page promptly, show its available content and controls, and fill slower sections independently. Start expensive reads when the user needs their results. Keep valid cached content visible while refreshing it.

Lazy loading determines **when work starts**. Asynchronous loading determines **where work runs and how the UI waits**. Progressive rendering determines **which content can appear before everything is ready**. This app needs all three. Adding a spinner to a synchronous query or decoding a full-sized image later would still cause stalls.

## Where to apply it

| Area | Show immediately | Start on demand | Loading experience |
| --- | --- | --- | --- |
| App entry | Minimum verified local routing state, then Home or Welcome | Remote profile refresh and ancillary maintenance once permitted by identity rules | Keep recovery/child-setup gates where required; no timer or decorative-image dependency |
| Home | Greeting, navigation actions, available local summary | Independent goal/reminder sections; optional content and images when visible or near-visible | Placeholder only for an unresolved section; other cards stay usable |
| Progress Overview | Overview layout and cached/local aggregate totals | Overview aggregate query when entering Progress | Charts and full session history do not gate Overview |
| Progress chart tabs | Tab heading and previous chart if available | Only the selected chart's bounded date-range query | Chart-sized placeholder on first load; retain chart during refresh |
| Progress History | History heading and cached first page | First page on selecting History; next pages near the end or via Load more | Inline footer progress/error; existing rows stay scrollable |
| Messages feed | Valid cached first page | Bounded background refresh on entry, subsequent pages on scroll/action | Cached list stays readable; refresh and pagination are separate states |
| Message detail | Cached detail if available, navigation/title otherwise | Detail fetch when opened | Body-sized loading/error region; Back remains usable |
| Family / child progress | Linked-child identities and compact summaries | Selected child's goals, weak words and paged history when selected | Each child/detail section loads independently; switch cancels previous detail work |
| Settings backup section | Theme/language controls and cheap backup summary | Conflict payloads and associated entry details only when review is opened | Keep an attention badge available without constructing every review row |
| Artwork | Reserved layout space and text/actions | Sized async image when its section is composed/approaching visibility | Lightweight placeholder that does not shift the page; see [image plan](image-delivery.md) |

“Immediately” means without waiting for unrelated work, not fabricated data. A count is unknown until its source is available; do not show `0` as a substitute for loading. Required local session association and durable saves remain correctness prerequisites. Recording/saving practice, sync outbox writes, notification de-duplication and essential reminder delivery must not depend on whether a card has been scrolled into view.

## Changes grounded in the current code

**Home:** [HomeScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeScreen.kt) eagerly composes a scrolling `Column`; make major sections individual keyed lazy-list items. [HomeViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/home/HomeViewModel.kt) currently combines required stats and japa into a single loading gate. Separate expensive optional sections so a slow section cannot withhold navigation actions. Retain the existing independent reflection refresh and remove unused child-history work from the Home query.

**Progress:** [ProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressViewModel.kt) combines overview, daily counts, accuracy, history, axis trends and japa before publishing one Success state. [ProgressScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/progress/ProgressScreen.kt) owns `selectedTab` only in the composable, so the data layer does not know which tab needs data. Make selected-tab demand available to the ViewModel, retain a small state per tab, and subscribe only to its necessary projections. Split the chart queries and History paging from Overview. Lazy rows alone do not bound database reads: the current History UI already uses a lazy list.

**Messages:** [TeachingRepository](../../core/data/src/main/java/com/mantrasiddhi/data/messages/TeachingRepository.kt) already limits feed pages to 30 and supports a cursor, while [MessagesScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesScreen.kt) already uses a lazy list and Load more. Keep these features; change the server-first return path to cache-first emission with background refresh. Separate initial loading, refreshing and appending in [MessagesViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/messages/MessagesViewModel.kt). Cache access also belongs off the UI thread where necessary. Continue filtering unpublished/expired content.

**Child detail cancellation:** [FamilyViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/family/FamilyViewModel.kt) already uses `selectedChildUid.flatMapLatest` for selected-child goals. Use this pattern for [ChildProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/childprogress/ChildProgressViewModel.kt): its `selectChild()` currently starts a new collecting job for every selection, and `clearSelectedChild()` only sets Idle. Earlier subscriptions remain active until ViewModel clearing, allowing redundant processing and an older child's emission to overwrite current detail state. Tie the detail flow to a nullable selected child ID, switch with `flatMapLatest`, and clear selection when the detail closes. Also guard results by account and child identity. This extends audit finding UI-09 with a concrete cancellation defect.

**Backup details:** [BackupViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/settings/BackupViewModel.kt) already uses `WhileSubscribed(5_000)`, but its combined state includes conflicts and all observed entries even for the status display. Split a cheap status/count projection from detailed conflict review. Query only entries referenced by opened reviews; preserve the background backup scheduler and show the attention state without waiting for detail decoding.

## Shared loading behavior

Use a small reusable section state that distinguishes:

- **Not requested:** no work yet because the tab/detail is unopened.
- **Initial loading:** no usable cached data; reserve approximately the final content bounds.
- **Content:** data is available, optionally marked refreshing or offline/stale.
- **Empty:** a successful result contains no items.
- **Error:** no usable data; provide Retry in the affected section.

For lists, track append progress/error separately from the first-page state. A refresh error keeps existing content with a small retry/status affordance. An initial error replaces only that section's placeholder. Preserve scroll position and stable item IDs when merging updates.

Use subtle static skeletons first. The app already has [LoadingSkeleton.kt](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/components/LoadingSkeleton.kt); reuse its shapes where appropriate, but avoid running shimmer animations for every offscreen card. Do not add an artificial minimum spinner duration. Keep placeholders out of accessibility focus and announce meaningful loading results without repeated announcements on recomposition.

## Request and cache ownership

1. UI emits demand: route visible, tab selected, child selected, review opened, next page requested or explicit Retry. Repository/ViewModel owns the operation. Never start reads in the composable body on every recomposition.
2. Use stable request identity: account/profile, entity, tab/filter/date range and pagination cursor. Deduplicate concurrent requests for that identity. Recomposition, lifecycle resume and scroll callbacks must not create duplicate fetches.
3. Publish valid local/cached data first. Refresh according to content freshness, account changes, explicit retry or local invalidation; avoid fetching again merely because the same page recomposed. Scope caches by account and clear inaccessible content immediately on sign-out/switch.
4. Suspend screen-only subscriptions when their destination is inactive. Use lifecycle-aware collection plus `WhileSubscribed` upstream where suitable. A short grace period can tolerate configuration changes; it must not leave full-history collectors running indefinitely. Cancel replaced reads, and reject late results even when the underlying network task cannot itself be cancelled.
5. Keep list prefetch small: initially one next page or approximately one viewport of images. Start critical first-viewport reads together where independent; do not introduce a waterfall of optional sections. Do not prefetch every tab or child profile after launch.

For long Room session history, consider Paging 3 with a projection that excludes unused transcription/word-score fields and stable ordering such as `(createdAt DESC, id DESC)`. Keep the existing Firestore cursor pagination unless a demonstrated need justifies replacing it. Android documents [paged data loading](https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data?authuser=7); lazy Compose layout independently controls which rows are composed, as described in [lazy lists](https://developer.android.com/develop/ui/compose/lists).

## First implementation slice and verification

Start with **Messages cache-first rendering, Progress tab-specific queries, Home lazy sections and child-detail cancellation**. These have identifiable current costs. Keep the image sizing work alongside this change, since lazy loading a 6 MiB bitmap still leaves a costly decode when it becomes visible.

Verify with controllable slow/failing repositories and then the device journeys in the [audit](README.md):

- Hold a section response indefinitely: the page's other controls and Back must still work.
- Open Progress Overview: no History or unselected-chart query should start. Open History: fetch one bounded page, then only the requested next page.
- Recompose/revisit a page: request counts remain deduplicated, cached content persists and scroll restores.
- Select child A, select B, then deliver A's delayed result: only B may appear. Close detail: its collection stops and cannot repopulate the closed view.
- Fail a Messages refresh or append: existing content remains readable and Retry targets only the failed request. Test cache miss and expired/deleted content separately.
- Switch accounts during a request: old-account results never appear in the new account's sections.
- Scroll Home quickly: image prefetch stays bounded; placeholders preserve layout; cached artwork is reused.

Record both tap-to-usable-content and eventual completion, plus query/request counts and active subscriptions. A page full of fast placeholders does not satisfy the goal if useful content arrives later than before. No device measurements or runtime improvements are claimed by this proposal.
