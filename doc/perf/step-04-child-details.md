# Step 04 — child-detail cancellation

Implemented and verified on 12 September 2026 for RW-04 and the child-detail cancellation finding in the lazy-loading audit.

## Changes

[ChildProgressViewModel](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/childprogress/ChildProgressViewModel.kt) owns one nullable detail selection and one `flatMapLatest` pipeline. Replacing or clearing that selection cancels both progress and weak-word sources before another detail starts. Re-selecting the active child does not restart work; selecting after an error creates a fresh request so retry remains possible. Errors are caught inside each selected flow, including errors constructing the sources, so a failed child does not terminate the selection observer.

Each selection carries the parent's local ID and Firebase UID, plus the selected child ID. Request reference identity also distinguishes A → B → A, close/reopen and same-child retries. Before publishing a detail result, the collector checks both the current request and the current parent identity; a buffered old result cannot replace the newer selection or reopen a closed detail. Results must identify the requested child and its parent, with the child profile role. Selection is restricted to the current parent's displayed children.

The existing current-user `StateFlow` supplies account identity. A different local parent, changed Firebase UID, sign-out or loss of the parent role clears selection and resets the overview while it reloads. Cosmetic edits to the same parent retain the detail subscription. Overview errors are caught inside the current account's flow, so they cannot terminate account observation and leave a detail collector alive after sign-out. This does not introduce a second account-resolution producer.

[ChildProgressScreen](../../frontend/android/src/main/java/com/mantrasiddhi/presentation/screens/childprogress/ChildProgressScreen.kt) already clears selection from the detail toolbar, system Back and error close button. A disposal effect now also clears it when the screen leaves composition while its ViewModel is retained.

## Verification

- **App unit suite:** 88 passed, zero failures, errors or skips. The [14 child-progress tests](../../frontend/android/src/test/java/com/mantrasiddhi/presentation/screens/childprogress/ChildProgressViewModelTest.kt) include nine new regressions covering counted subscriptions across A → B → A → close; duplicate selection; inactive-child updates; queued updates with rapid replacement/close; account switches, sign-out and role loss; same-account edits; close during loading; source construction/collection failures and retry; invalid child results; account cancellation after an overview error; and ViewModel clearing.
- **Subscription counts:** the overview retains one progress collector per child. A selected detail adds exactly one progress collector and one weak-word collector for that child. Switching returns the old child to its overview baseline; closing returns both children to that baseline. The measured per-child peaks are two progress collectors and one weak-word collector, including the overview. ViewModel clearing leaves zero of either source.
- **Build and lint:** debug app and instrumentation APKs build; lint has zero errors, 14 warnings and three hints. An initial instrumentation compilation error from a `Target` import was corrected before the successful build.
- **Physical-device UI checks:** three passed on the Pixel 7 Pro (Android 17). [ChildProgressCancellationTest](../../frontend/android/src/androidTest/java/com/mantrasiddhi/presentation/ChildProgressCancellationTest.kt) uses the real ViewModel, domain use cases, Android Main dispatcher and Compose with disposable in-memory repositories. It checks A → B → A with toolbar close and subsequent data updates, system Back, screen disposal with a retained ViewModel, and account replacement/return. Repository session subscriptions are three for the selected child (overview, detail progress and weak words), one for each unselected child, and zero after ViewModel clearing. Initial locked-screen runs were rerun after the user unlocked the phone; a test selector was corrected to tap the foreground detail toolbar instead of the covered overview toolbar before the passing run.
- **Patch whitespace:** `git diff --check` passed.

Final local evidence: `build/perf-04-final-verification.log` (88 unit tests, both debug APKs and lint), `build/perf-04-device-test-build.log` (final instrumentation selector fix), `build/perf-04-device-passed.log` (`OK (3 tests)`), and standard Gradle XML/lint reports. The debug APK was installed on the connected Pixel 7 Pro for instrumentation; tests do not seed or modify real child records.

## Scope and limits

This closes the accumulating selected-detail work. The overview still observes every child's progress while its ViewModel is alive; sharing summaries, paging history and general lifecycle demand remain step 09. These checks establish cancellation and stale-result isolation, not a startup/frame-time improvement, production-account audit or two-device sync result. Account cancellation follows the existing local current-user state; shared verified account resolution remains step 05.
