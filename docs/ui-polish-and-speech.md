# UI polish and Hindi speech support

Verified on `codex/lotus-ui-overhaul`, 6 September 2026, against the owner's current backend changes.

## Visible changes

- Six standard Google Material Symbols Rounded replace the custom navigation icons. Selection fades in place over 140ms, with no sliding or stretching pill. Existing routes, selected-tab semantics, and 48dp touch targets remain.
- A generated photographic sandalwood mala replaces the drawn Purashcharana ring. The original lotus stays in the center. Counts and progress retain their existing controls.
- Home's reflection is a reading section with a serif title, a sage rule, a note count, and explicit Read more, Show less, and Next note actions. All eight notes and the daily starting note remain.

Sources: [Material Symbols](https://developers.google.com/fonts/docs/material_symbols), [local icon provenance](../frontend/android/design/icons/README.md), [artwork prompts](artwork.md). The icons are bundled with the Apache 2.0 license; the eighth bitmap is bundled locally with real alpha transparency.

## Speech fix

The previous gate checked only the dedicated on-device recognizer, omitted the manifest query for recognition services, and classified failed support checks as missing packs. Recording and model checks now share service selection, language, and intent options.

The resolver prefers a confirmed installed Hindi pack, checking both the dedicated recognizer and the phone's default speech service. Installed language tags accept the existing Hindi matching rules, including underscore tags. Pending, downloadable, unsupported, and unknown support remain separate. Keyboard languages, TTS voices, and online-only Hindi are not treated as installed recognition packs.

When a service cannot report support, the screen offers an explicit Try listening action. The request prefers offline recognition, and the provider controls whether it honors that preference. This is not a guarantee of offline processing on every default service. A confirmed missing or unsupported pack does not enable that trial. The dedicated on-device recognizer remains preferred whenever it confirms Hindi support.

Repeated resume checks cancel older requests. A transient metadata error does not discard a previously confirmed pack, while an actual language error invalidates it. Timed-out and cancelled checks release their recognizers; ending a microphone stream releases the recognizer even after its coroutine is cancelled. Error codes 12 and 13 now distinguish unsupported language from a missing model. Error 14 reports unknown support.

Android references: [SpeechRecognizer and service visibility](https://developer.android.com/reference/android/speech/SpeechRecognizer), [RecognitionSupport language lists](https://developer.android.com/reference/android/speech/RecognitionSupport), [offline preference](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE).

## Verification

| Check | Result |
| --- | --- |
| Domain unit tests | 335 passed |
| Data unit tests | 39 passed |
| ViewModel unit tests | 32 passed |
| Compose interaction tests | 12 passed on API 37 |
| Database and speech platform tests | 10 passed on API 37 |
| Debug and minified release builds | Passed |
| Android lint | 0 errors, 12 warnings, 2 hints |
| Staged whitespace check | Passed |

Total: 428 passing tests, none skipped. The two additional lint warnings concern the length of Google's upstream book and settings vector paths; their geometry is retained unchanged.

New coverage includes default-service Hindi support, native/default selection, Hindi locale tags, missing and pending packs, online-only Hindi, unavailable metadata, explicit trial persistence, later invalidation, stale checks, timeouts, cancellation, microphone cleanup, Android error codes, and reflection cycling. An instrumented platform check verifies the actual recognition intent and service visibility.

The Android 17 emulator reported `native=true default=true state=NOT_INSTALLED installed=[]`. Its selected system service was Google's `GoogleTTSRecognitionService`. The actual gate displayed the Hindi download screen rather than reporting no recognizer. This emulator does not have a confirmed Hindi pack. The owner's Pixel 7 Pro on Android 17 beta QPR2 and actual Hindi microphone transcription still need physical-device verification.

Reviewed Home, reflection, and Purashcharana in light and dark themes, plus 320dp width at 150% text. Expanded the full reflection at that size and verified Show less remained reachable. The [gallery](ui-review/README.md) distinguishes sample UI from the actual service check. No account or cloud data was created.

## Scope and artifacts

A SHA-256 snapshot of 469 existing tracked and untracked files was compared after implementation. Changes outside the intended UI, speech, and documentation files were absent. The owner's unrelated backend work remains uncommitted, including the pre-existing headers and whitespace in the three shared speech/domain files. Scoring, counters, profiles, storage, and synchronization were not changed by this pass.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| `frontend/android/build/outputs/apk/debug/app-debug.apk` | 58,912,470 | `e1173f06dc17f95bc51dfb0f5749c5c053221d03a2de2b729a25b32ecff8ce4d` |
| `frontend/android/build/outputs/apk/release/app-release.apk` | 17,717,480 | `3d1c09dcdabc659409358468a1d6eda3a6ecfae3a914f0d1196e2bddd50e710d` |

Local build and test logs use the `third-pass-` prefix. The debug-only review activity and its service probe are not part of the release build.
