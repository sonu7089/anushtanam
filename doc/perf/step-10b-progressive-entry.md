# Step 10b — Progressive app entry

12 September 2026. Follow-up to [step 10](step-10-startup-images.md) and the [progressive-loading proposal](lazy-loading.md).

## Behavior

Home is the initial navigation destination and stays mounted while account resolution and local profile opening run: neutral Home heading, empty avatar/count slots, shared lotus artwork and disabled Practice/Japa cards and navigation. Before verification its producer is absent and the shared layout receives no profile state, so no Home data subscriptions start. Artwork remains asynchronous. Static placeholders replace Home's initial spinner and section progress animations; an unknown count is never rendered as zero.

After profile verification, the same Home composition receives its producer and updates the greeting/avatar and practice actions. Step 09's independent Home producers continue filling japa, goals and the vow section as each source responds. A slow goal query does not block practice or a ready japa value. Failed sections retain their existing retry actions. Loading no longer replaces the scaffold, list or navigation destination. Shared row bounds, a reserved streak slot and the retained list state prevent the first cards or scroll position from jumping. Ordinary navigation transitions remain as before.

Protected destinations and the guide sheet check entry readiness before showing personal content, including after back-stack restoration. Home remains in its neutral state during verification; other protected destinations use the same fallback layout. A confirmed signed-out or child-setup decision returns through the auth gate. Interactive sign-in continues to own opening its local session after a confirmed signed-out state. Recovery still exposes Retry and Switch profile.

## Readiness and limits

`perf_entry_shell` identifies the placeholder layout. It does not call `onStartupReady`, publish `perf_home_ready`, or satisfy `ReportDrawnWhen`. Disabled navigation uses separate `entry_tab_*` tags. Existing Home/Welcome usable-content signals remain the benchmark targets.

This improves what appears during required startup work; it does not remove Android process startup, account verification or local profile opening. No artificial loading duration, delayed permission bypass, new cloud cache or new background timer is introduced. A signed-out cold launch may briefly show the neutral shell before Welcome. Family navigation appears only after the profile role is known.

## Verification

### Stationary handoff refinement (12 September 2026)

User testing exposed a remaining whole-page handoff: the first implementation replaced a standalone shell with a new Home scaffold/list, and their row sizes differed. The refinement makes Home itself the startup destination and updates its nullable producer/state in place. The standalone preview/fallback now uses that same layout. The extra loading-text row is removed; the heading instead exposes an accessibility loading description. Android's default splash-exit overlay is removed immediately at first content readiness, leaving the mandatory native launch screen but avoiding another reveal over Home.

Verified 114 app unit tests and 24 Pixel Compose tests. Two additional regressions compare the header, hero, practice cards and goal bounds across 48 handoff frames (192 exact bounds comparisons), assert the Home destination stays mounted once, and retain a scrolled position at 320 dp / 1.5× text in dark mode. Build/unit/lint log: `step10b-refine-check.log`; native-exit build/lint log: `step10b-refine-final-build.log`; final device log: `step10b-refine-final-device.log`. Lint still has zero errors. The installed debug app was cold-launched and reached `perf_home_ready`; screenshots/XML and the installed APK hash are under `build/step10b-refine/`. No new timing speedup is claimed by these layout checks.

### Original 10b measurements (before the stationary handoff refinement)

- 114 app unit tests pass, including existing account resolution/replacement, recovery, child setup, interactive sign-in and independent Home data coverage.
- 22 Pixel 7 Pro Compose tests pass: six new progressive-entry regressions plus the existing Home/navigation/accessibility and practice-guide suites. They cover hidden personal content during verification/recovery, disabled cards/tabs, auth routing callbacks, interactive-session readiness, pending counts/goals, replacement of a loaded screen by the shell, and a 320 dp / 1.5× text layout.
- Light/dark fixture screenshots at the phone's normal size, plus 320 dp / 1.5× text screenshots before and after scrolling, were visually checked. Disabled Japa text initially inherited an unsuitable white content color; explicit disabled colors corrected it before the final verification. Resolution and font scale were restored.
- Debug and optimized benchmark APKs build; lint reports zero errors (14 existing warnings and three hints). The first device run was interrupted by the lock screen; the final unlocked run passes all 22 tests.

Build/unit/lint log: `step10b-verified-build.log`. Final Compose log: `step10b-device-final.log`. Screenshots and APK hashes: `build/step10b/`.

Both optimized startup tests passed with 10 measured launches each and the unchanged `perf_home_ready` target. [Results and samples](step-10b-results.json):

| Metric, median (ms) | Cold | Warm |
| --- | ---: | ---: |
| Android initial display | 271.838 | 68.256 |
| Android fully drawn / usable-content report | 2,379.943 | 173.065 |
| External launch-to-usable observer | 2,610.304 | 327.441 |

External observer p95 (nearest rank) is 4,045.099 ms cold / 390.448 ms warm. Android initial display is window timing, not a separately instrumented shell-layout timestamp. The distinct shell tag and UI tests verify that placeholders are not counted as usable Home. This is a focused verification, not a controlled speed comparison: step 10 used 20 samples per mode, thermal state differed (this run 0 → 1), and profile opening still controls readiness. Raw samples and 20 Perfetto traces plus benchmark metrics are under `build/step10b/{startup,traces}`; runner log: `step10b-startup.log`.

The final debug APK was reinstalled. An additional background-process-death check changed PID 19223 → 19517, showed the neutral shell, then reached the existing recovery state. The reason for that profile-open failure was not established; no guard was bypassed. Tapping Retry returned to `perf_home_ready` with the signed-in profile. Screenshots/XML are retained under `build/step10b/entry-*`; the device was left on verified Home with its original resolution, font scale and screen timeout. Child-setup/signed-out behavior is covered by state/UI tests rather than signing the user's phone out for this presentation change.
