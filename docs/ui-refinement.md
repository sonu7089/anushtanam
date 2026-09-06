# UI refinement, 6 September 2026

Implemented on `codex/lotus-ui-overhaul`, on top of the current working tree with the owner's backend edits.

## Changes

- Four additional generated images, for seven bundled images in total. River and family scenes fade into page backgrounds; the diya and jasmine sit directly on the page. Existing lotus, ritual, and emblem images are retained.
- Floating rounded navigation with six custom SVG icons and a spring-animated selection pill. Parent profiles retain six destinations; child profiles retain five. SVG sources are in `frontend/android/design/icons`, with a converter in `scripts/generate-navigation-icons.py`.
- Home Practice and Japa arrows share a full-width title row and align to the trailing edge.
- The Advanced Vedic control is removed from Settings. Previously saved preferences and evaluation behavior are untouched.
- Sandhya period labels and counts sit above full-width bars. Weekly bars have a fixed 72dp plot lane, with dates outside it. The vertical scale includes both scheduled and recorded counts, so extra observations cannot extend beyond the chart. Displayed counts remain unchanged. Screen readers receive each day's actual counts.
- 225 existing UI strings revised for simple, warmer English. All 528 existing string IDs and their formatting placeholders are preserved. No em dashes remain in the string resources. Sanskrit mantra content is unchanged.

## Verification

| Check | Result |
| --- | --- |
| Debug APK | Passed |
| Minified release APK | Passed |
| Domain unit tests | 335 passed |
| Data unit tests | 23 passed |
| ViewModel unit tests | 29 passed |
| Compose interaction tests | 10 passed |
| Room integration and migration tests | 8 passed |
| Android lint | 0 errors, 10 warnings, 2 hints |

Total: 405 passing tests. The four added Compose checks cover every parent tab at 320dp, the five child tabs, weekly chart bounds after completion and extra observations, and full-width period bars with 150% text. The existing Settings test also checks that Advanced Vedic is absent.

Fifteen new screenshots show the final UI on an API 37 emulator, including white and dark themes, all new assets, completed charts, and 320dp layouts with 150% text. The emulator was a separate read-only instance; display overrides were restored after review. See the [gallery](ui-review/README.md).

## Scope and preservation

A SHA-256 baseline of tracked files was taken before editing. Every tracked file outside the explicit UI and documentation changes still matches that baseline, including the owner's domain/data changes, Gradle changes, README, and architecture document. Existing untracked backend additions were not edited or staged with this UI work. No service rules, persistence schemas, authentication, speech evaluation, counting, reminder scheduling, or cloud synchronization logic changed.

The review uses local fixtures and does not exercise real microphone recognition, signed-in Firebase round trips, or notification delivery on physical devices. The navigation is inspired by the rounded floating iOS tab bar and implemented with native Compose; it does not use Apple's Liquid Glass rendering engine.

## Reproduce

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --console=plain
.\gradlew.bat :core:data:assembleDebugAndroidTest --offline --console=plain
```

Install the app and test APKs on an emulator, then run `androidx.test.runner.AndroidJUnitRunner` for `shastralife.anushtanam.test` and `com.mantrasiddhi.data.test`. `scripts/capture-ui.ps1` supports the added `sandhya-complete` fixture. The review activity and fixtures are debug-only.

Built artifacts:

| APK | Size | SHA-256 |
| --- | --- | --- |
| `frontend/android/build/outputs/apk/debug/app-debug.apk` | 56.18 MiB | `29dad766ee6668eaddecd2b7d8c2a133c72a0a2006ce2b54c7d0110cd84fcde4` |
| `frontend/android/build/outputs/apk/release/app-release.apk` | 16.40 MiB | `98440657849a1f5dbb287eb65863c28f3637476225c312e6bd77c6abfb06eb1e` |

Image prompts and font provenance are in [artwork.md](artwork.md).
