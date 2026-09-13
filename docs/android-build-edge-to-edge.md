# Android build and window compatibility

## Configuration

- AGP 8.13.2 and Gradle 8.13; Kotlin 1.9.24, Compose compiler, SDK levels and QR scanner dependencies are unchanged.
- Release builds use R8 full mode, `proguard-android-optimize.txt`, resource shrinking, and `android.r8.optimizedResourceShrinking=true`.
- No keep rules were weakened. The scanner was not removed or replaced to silence Play warnings.

## Window behavior

The app's minimum SDK is 33. `configureAppWindow()` lays out the window behind system bars with `WindowCompat.setDecorFitsSystemWindows(window, false)`. Its activity theme uses the supported `always` cutout mode. Only the pre-35 theme sets the status-bar color. The base theme keeps a transparent navigation-bar color even on API 35+: three-button navigation still uses it, and inheriting Material's opaque color forces a light navigation strip even in app dark mode. Navigation-bar contrast protection stays enabled; the Compose theme continues to update light/dark status and navigation icons. There are no runtime system-bar color setter calls.

This follows Android's documented manual setup for the supported OS range and avoids retaining `enableEdgeToEdge()`'s compatibility implementations for older OS versions. If the minimum SDK is ever lowered, revisit this setup. Do not replace the helper with `enableEdgeToEdge()` without rechecking the minified artifact, since library code can reintroduce the deprecated setters.

The main navigation scaffold consumes horizontal safe-drawing insets. Screens still own their vertical insets. The floating navigation bar uses horizontal and bottom safe-drawing insets, including cutouts. The startup placeholder and review/benchmark fixtures follow the same ownership to prevent duplicate padding.

## Verification commands

```powershell
.\gradlew.bat :app:assembleRelease :app:bundleRelease :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.mantrasiddhi.presentation.EdgeToEdgeUiTest,com.mantrasiddhi.presentation.LotusUiTest'
python scripts/check-window-apis.py frontend/android/build/outputs/bundle/release/app-release.aab
```

The binary check rejects the two deprecated setter names in every DEX file. It cannot identify inlined numeric cutout constants or predict Play's server-side assessment. Check the release mapping for retained `EdgeToEdgeApi*` implementations as well. `EdgeToEdgeUiTest` dispatches status/navigation/cutout insets through an actual activity and verifies title/navigation bounds and navigation to Settings.

## Results (2026-09-13)

- Release APK, signed AAB, debug APK and minified benchmark APK built successfully.
- 635 JVM tests passed across domain, data and app modules.
- 14 UI tests passed, including dispatched cutout insets, Home/Settings navigation and dark-mode navigation contrast.
- Four `IdentityMigrationTest` tests passed, including the production migration chain from every schema version 1–16 to 17 and reopening through the production database builder.
- Lint: zero errors, 14 warnings and three hints.
- Both release APK and AAB passed the DEX setter-name audit. No `EdgeToEdgeApi*` classes remained in the release mapping; the app theme uses `always`, not short edges.
- Visually checked the homepage in portrait and dark Settings in landscape with a simulated tall cutout and three-button navigation. Confirmed minified benchmark Home → Settings navigation and signed release startup to the welcome screen.

Device runs used an isolated Android 17 emulator data image. An initial run against the existing emulator stopped on a schema mismatch (an extra `user_profiles.parentId` index); no database source was changed as part of this work. Supported historical-schema migration tests passed in the isolated run. Android 13/14 and Android 15/16 device coverage, real camera/QR scanning, and Play Console's post-upload scan remain unverified. No bundle was published.

## Sources

- [AGP 8.13 compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Optimized resource shrinking](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Manual edge-to-edge setup](https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually)
- [Compose cutout handling](https://developer.android.com/develop/ui/compose/system/cutouts)
