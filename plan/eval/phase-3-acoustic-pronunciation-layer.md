# Phase 3: Acoustic Pronunciation Layer

**Duration**: ~6 days
**Goal**: Measure pronunciation from the **actual audio**, not from dictation text. Capture the learner's raw
audio, extract acoustic features, and compare each word against the corresponding segment of the bundled
benchmark recitation using **Dynamic Time Warping (DTW)**. This is the architectural leap that makes the app
genuinely able to teach pronunciation (§2.1).

**Depends on**: Phases 1–2 (three-axis model, reference object with benchmark timestamps, phoneme structure).
**Risk**: High — new audio pipeline, real-time constraints, device-mic variability. Mitigations below.

> **Why this and not forced-alignment/GOP or Whisper?** A Sanskrit/Vedic phoneme acoustic model is not
> available off-the-shelf and would be large/heavy. DTW against our **own benchmark audio** needs *no model*,
> runs on-device in milliseconds, reuses the `benchmark_timestamps.json` segments we already ship, and yields
> a per-word/per-syllable pronunciation-similarity-to-reference signal plus rhythm/timing deviation. It is the
> highest value-to-risk option.

---

## 3.1 Parallel audio capture

**Problem addressed**: §2.1 — STT text hides pronunciation; we need the waveform.

### Tasks
- [ ] Add `AudioCaptureService` using `AudioRecord` (16 kHz mono PCM) that records the learner's audio buffer
  **in parallel** with `SpeechRecognizer` (the recognizer does not expose raw audio). Manage mic focus and
  lifecycle alongside the existing speech flow in `RecitationViewModel`/`PracticeModeViewModel`.
- [ ] Buffer audio in memory for the duration of one recitation (short; no disk needed for Practice mode).
  Optionally persist the last attempt for "replay your recitation."
- [ ] Verify only one component owns the mic at a time; handle the `RECORD_AUDIO` permission already present.

### Deliverables
- `data/audio/AudioCaptureService.kt`
- `data/audio/PcmBuffer.kt`

### Acceptance Criteria
- Audio captured concurrently with STT without breaking transcription.
- No mic leak / focus deadlock on screen exit (mirror existing `BenchmarkAudioPlayer` lifecycle discipline).

---

## 3.2 Feature extraction (MFCC)

### Tasks
- [ ] Implement on-device **MFCC** (or log-mel) extraction in pure Kotlin (frame → window → FFT → mel filter
  bank → DCT). Keep it in the data/`signal` layer; expose plain float arrays to domain so the domain layer
  stays Android-free.
- [ ] Pre-compute and cache MFCCs for the benchmark audio **per word segment** (using
  `benchmark_timestamps.json` ranges) at first launch.
- [ ] Add cepstral mean normalization to reduce mic/channel variability.

### Deliverables
- `data/signal/MfccExtractor.kt`, `data/signal/Fft.kt`, `data/signal/MelFilterBank.kt`
- `data/signal/BenchmarkFeatureCache.kt`

### Acceptance Criteria
- MFCC output validated against a reference vector (golden numbers) within tolerance.
- Benchmark features cached once; subsequent evaluations reuse the cache.

---

## 3.3 Per-word DTW pronunciation scoring

**Problem addressed**: §2.1, §2.2 — a *real* pronunciation signal grounded in a correct recitation.

### Tasks
- [ ] Segment the learner audio into words by mapping STT word boundaries / energy-based VAD onto the buffer,
  aligned to the reference word order produced by the Phase-2 text alignment.
- [ ] For each reference word, run **DTW** between learner-segment MFCCs and the cached benchmark-segment
  MFCCs → a normalized distance → per-word acoustic pronunciation score (0..1).
- [ ] Derive **rhythm/duration** deviation per word (learner segment length vs benchmark length) → feeds a
  "pacing" sub-signal (too fast / too slow).
- [ ] Pure-Kotlin DTW in domain (`domain/evaluation/acoustic/`), consuming feature arrays from the data layer.

### Deliverables
- `domain/evaluation/acoustic/Dtw.kt`
- `domain/evaluation/acoustic/AcousticPronunciationScorer.kt`
- `domain/evaluation/acoustic/WordSegmenter.kt`

### Acceptance Criteria
- A clearly mispronounced word scores meaningfully lower than a correct one **even when STT text matches**
  (the case impossible to detect before — added to the calibration corpus with recorded samples).
- DTW per recitation completes within the latency budget (§3.5).

---

## 3.4 Fusion: text + acoustic → final three axes

### Tasks
- [ ] Update `PronunciationScorer` to **fuse** the Phase-2 text-phonetic estimate with the Phase-3 acoustic
  DTW score. Acoustic is the primary pronunciation signal; text-phonetic is the fallback when audio is
  missing/low-quality (graceful degradation when `AudioRecord` unavailable or STT-only path).
- [ ] Keep **completeness** and **correctness/sequence** primarily text-driven (STT is good at "which words /
  what order"); use acoustic energy/VAD as a corroborating signal for completeness (did they actually
  vocalize a word the STT dropped, or vice versa).
- [ ] Confidence/quality gating: if SNR or STT confidence is too low, mark pronunciation as "not assessed —
  noisy audio" instead of penalizing.

### Deliverables
- Updated `domain/evaluation/scoring/PronunciationScorer.kt`
- `domain/evaluation/acoustic/AudioQualityGate.kt`

### Acceptance Criteria
- Hybrid result fills all three axes; with audio disabled, engine degrades cleanly to the Phase-2 text-only
  result (no crash, clearly flagged).
- Calibration pronunciation-axis agreement improves vs Phase-2 (now grounded in audio).

---

## 3.5 Performance, threading & robustness

### Tasks
- [ ] Run feature extraction + DTW off the main thread (`Dispatchers.Default`); only emit final
  `RecitationAssessment` to UI.
- [ ] Budget: full hybrid evaluation < **300 ms** after audio stops on a mid-range device; text path remains
  < 50 ms.
- [ ] Handle: no/short audio, clipping, background noise, very fast/slow recitation, partial recitation.
- [ ] Memory: cap buffer size; release PCM + feature arrays promptly.

### Acceptance Criteria
- Latency budget met on a representative device.
- No ANR / OOM across the robustness cases above.

---

## Exit Criteria

| Criterion | Metric |
|---|---|
| Audio captured | PCM recorded in parallel with STT, no mic conflict |
| MFCC validated | Matches golden vectors; benchmark features cached |
| DTW pronunciation | Per-word acoustic score detects mispronunciation STT text hides |
| Fusion | Three axes filled from text + acoustic; clean degradation without audio |
| Performance | Hybrid < 300 ms; text < 50 ms; no ANR/OOM |
| Calibration | Pronunciation-axis agreement improves vs Phase-2 |
