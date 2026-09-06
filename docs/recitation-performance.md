# Recitation responsiveness — 6 September 2026

Japa and Practise now finish local work without waiting for cloud acknowledgement. Scoring thresholds, phonetic weights, confidence rules, and hypothesis selection are unchanged. The changes below fix execution and lifecycle problems; the proposed evaluation redesign remains unimplemented.

The subsequent [deferred cloud sync change](deferred-cloud-sync.md) replaces the initial best-effort background queue with a durable, batched outbox and a one-minute foreground cadence. The verification counts below record this initial responsiveness pass.

## Findings and fixes

| Finding | Change |
| --- | --- |
| Progress synchronization waited for a Firestore read and write before returning feedback. Offline writes could wait indefinitely. | Ordered background synchronization; Firestore owns submitted offline writes. Capture the initial local seed before enqueueing, so later queued attempts are not included in that seed and incremented again. |
| Self-target, Purashcharana, and child-goal cloud work held up Japa. | Local updates remain sequential. Self-target writes no longer wait for server acknowledgement; Purashcharana mirrors and child-goal increments run independently of local feedback. Network workers have bounded waits. |
| Phonetic scoring ran on the main thread and repeated tokenization and comparisons. | Score on `Dispatchers.Default`; cache tokens, word-pair similarities, and hypothesis assessments within one call. Confidence tie-breaking and acoustic fallback remain intact. |
| Learner level and the Japa recent list loaded the full history on every attempt. | SQL limits to 20 and 5 sessions respectively, backed by a `(userId, createdAt, id)` index. The 20-session window preserves both learner-level session floors. |
| Cloud initialization materialized history to derive small summaries. | SQL aggregates return totals, best score, and correct daily counts without loading transcripts or decoding word-score maps. |
| Identical partial transcripts repeatedly reset the end timer. | Only changed, nonblank transcripts reset activity. Both scripts retain the existing closing-word signal. |
| Providers could stop capturing but never deliver a final transcript. | Forward end-of-speech, allow a bounded final-result grace period, then use the existing transcript fallback. |
| Practise could remain listening when no text arrived or a provider closed silently. | No-speech timeout, maximum recording duration, and empty-flow handling exit recording with results or a useful error. |
| Synchronous callbacks could finish before the recording job was assigned. | Assign lazy jobs before starting them, making cancellation reliable even for immediate callbacks. |
| Cancelled evaluation could overwrite a reset screen; Japa partials could erase feedback. | Track and cancel the evaluation job, respect cancellation before publishing, and preserve current feedback during speech/target updates. |

Timing uses a monotonic clock. The existing closing-word signal is handled immediately in Japa and within a 200 ms watchdog tick in Practise. Transcript inactivity retains the existing 2-second threshold. A reported end-of-speech gets up to 1.5 seconds for a final result, plus the watchdog tick. Practise exits after 15 seconds without a transcript and caps one recording at 120 seconds. These are software bounds, not measured microphone latency promises.

Correct Japa now produces only a soft, three-ring ripple. App success and achievement sounds are removed from this path. The ripple lasts 1.1 seconds, has no full-screen color flash, does not intercept taps, and retains the reduced-motion fallback. The navigation pill moves over 320 ms; tab screens use matching directional slides. Detail transitions remain 220 ms. The debug review activity shares the production transitions and includes a local ripple fixture.

## Verification

- 338 domain tests, 41 data unit tests, 42 ViewModel tests, and 24 emulator tests passed: **445 total, none skipped**.
- Regression coverage includes silent providers, duplicate hypotheses, missing final callbacks, final confidence preservation, synchronous completion, cancelled evaluations, consecutive Japa with a stalled cloud goal, silent success, ordered background work, and stalled-worker recovery.
- Room v7→v8 migration preserves existing sessions. Emulator tests verify index use, bounded ordering, and aggregate totals. No destructive migration is used.
- Debug and optimized release APKs build successfully. Android lint has no errors; the pre-existing 12 warnings and 2 hints remain.
- A warm desktop JVM benchmark of three text hypotheses over 100 iterations measured approximately **0.17 ms median / 0.34 ms p95**. It excludes microphone capture, speech-provider latency, database work, and rendering. This is not an Android device benchmark.
- Captured and reviewed navigation and repeated ripple animations on the Android 17 Lotus emulator. Local recordings and frame sheets are in `build/performance-navigation*` and `build/performance-ripple*`.
- Build and test logs are in `build/performance-*.log`.

Real Gayatri microphone recitation on the owner's phone, live signed-in cloud behavior, and offline/reconnection round trips still require device verification. The initial background queue described here has since been replaced by the [durable sync outbox](deferred-cloud-sync.md). Local sessions and counts remain in Room. This pass does not claim that every possible application bottleneck has been eliminated.

Android documents that speech callbacks depend on the provider, including optional partial results: [RecognitionListener](https://developer.android.com/reference/android/speech/RecognitionListener). Firestore documents its cached/offline write behavior: [Access data offline](https://firebase.google.com/docs/firestore/manage-data/enable-offline).

## Evaluation proposal — not implemented

Both current recording flows supply no raw audio. They can assess recognized words and completeness, but transcript similarity cannot establish the learner's actual pronunciation or pitch. Another limitation is that Japa commits segments from provisional partial text with assumed confidence; a later recognizer revision or dictation correction can conceal an error. Changing those acceptance rules requires a separate decision.

I propose one microphone stream feeding recognition and audio analysis, with separate word/completeness and pronunciation results. Use a stable closing phrase plus speech activity to establish the endpoint, and distinguish “not heard clearly” from an incorrect recitation. Keep immediate capture feedback separate from the confirmed count. Calibrate the existing acoustic path against labeled recordings before making it an acceptance gate.

Evaluate that design with speaker-disjoint recordings: correct chants, omissions, substitutions, repetitions, slow chants, natural pauses, regional accents, and background noise. Report false accepts, false rejects, unclear-audio outcomes, and p50/p95 time from the last voiced syllable to visible feedback on representative phones. Include consecutive recitations, airplane mode, reconnection, and long histories. Transcript fixture accuracy alone cannot establish whether this is reliable enough for daily use.
