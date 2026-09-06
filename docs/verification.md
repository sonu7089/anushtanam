# Redesign verification

The [deferred cloud sync update](deferred-cloud-sync.md) adds persistent offline queues, one-minute foreground checks, batched uploads, and duplicate-safe retries, with 464 passing tests.

The subsequent [recitation responsiveness pass](recitation-performance.md) documents the latency fixes, silent ripple, navigation motion, 445 passing tests, and the evaluation proposal left for review.

The latest navigation, mala, reflection, and speech updates are documented in [ui-polish-and-speech.md](ui-polish-and-speech.md), with 428 passing tests and physical-device limits. The preceding refinement is documented in [ui-refinement.md](ui-refinement.md). Both were tested against the owner's current backend edits on 6 September 2026. The results below describe the original design pass.

Verified on branch `codex/lotus-ui-overhaul`, 5 September 2026.

## Builds and checks

| Check | Result |
| --- | --- |
| Debug APK | Built successfully |
| Minified, resource-shrunk release APK | Built successfully |
| Domain unit tests | 288 passed |
| Data unit tests | 23 passed |
| ViewModel unit tests | 29 passed |
| Compose interaction tests | 6 passed |
| Room integration and migration tests | 7 passed on API 37 |
| Android lint | No errors; 10 warnings and 2 hints |
| Firebase function JavaScript syntax | Passed |
| Git whitespace check | Passed |

UI coverage checks both welcome role actions, every home destination, stored theme/script values, selection semantics, disabled recording while processing, transliteration toggling, and truthful progress announcements during animation.

The Android test dependencies were updated to AndroidX JUnit extensions 1.3.0 and Espresso 3.7.0 for Android 17 compatibility. The production Compose/Material toolchain remains unchanged. See the [official AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).

## Preservation audit

Compared 183 original domain, data, dependency-injection, and ViewModel source files against the moved files. Their implementation bodies are unchanged; the comparison excludes package/import statements and whitespace. The session synchronization use case moved into the data layer because it depends on concrete preference services.

Reference audio, timestamp assets, and all three exported Room schemas match the originals. Application identity, schema versions, preference keys, Firebase paths, and signing configuration remain stable. No Firebase deployment was performed.

## Visual checks

Reviewed the principal screens in light mode, representative screens in dark mode, and compact layouts at 320dp with 150% text. Fixed squeezed summary columns, stacked narrow cards, crowded family metrics, chip spacing, compact navigation, and tinted dialog backgrounds during this review. See the [screenshot gallery](ui-review/README.md).

The fourteen principal text/background pairs in the curated light and dark palettes exceed 4.5:1 contrast. Secondary labels now use opaque theme foregrounds. This is a palette and visual audit, not a complete automated accessibility certification.

## Limits

The tests and fixture gallery do not exercise real microphone recognition, signed-in Firebase round trips, push delivery, or reminder delivery on physical devices. The underlying services and their business rules were preserved. Material 3 components and expressive spring motion are implemented on the existing stable toolchain; the newer experimental M3 Expressive APIs were not introduced.
