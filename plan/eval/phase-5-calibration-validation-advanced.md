# Phase 5: Calibration, Validation & Advanced Recitation Quality

**Duration**: ~4 days (core) + optional swara stretch
**Goal**: Prove the engine actually agrees with human judgment, lock that in as a CI gate, and (optionally)
add Vedic **swara/pitch** accuracy — the deepest dimension of correct Gayatri recitation.

**Depends on**: Phases 1–4 (three axes, acoustic layer, calibration harness foundation).
**Risk**: Medium. Calibration is data-gathering work; swara is a research-grade stretch goal.

---

## 5.1 Grow & label the calibration corpus

**Problem addressed**: §2.10 — thresholds are guesses; no proof the evaluator is "correct."

### Tasks
- [ ] Expand the Phase-1 corpus with **recorded audio** samples (not just transcripts) spanning: native vs
  learner speakers, each pada missing, each common mispronunciation class (de-aspiration, dental/retroflex,
  vowel-length, dropped visarga/anusvara), pacing errors, noisy mic, child vs adult voices.
- [ ] Collect **human labels** per axis (ideally from someone who knows correct recitation) — store as
  `CalibrationCase` rows with audio asset references.
- [ ] Keep all corpus data on-device/in-repo test resources only (privacy; demo app).

### Deliverables
- Expanded `src/test/resources/eval/corpus.json` + audio fixtures.
- Labeling guidelines doc.

### Acceptance Criteria
- Corpus covers every axis and every major error class with ≥ a few samples each.

---

## 5.2 Threshold tuning & agreement reporting

### Tasks
- [ ] Sweep `EvaluationConfig` weights/cutoffs against the corpus; select values maximizing per-axis agreement
  with human labels (rank correlation + pass/fail agreement). Record chosen values and their justification.
- [ ] Produce an **agreement report** (per-axis correlation, confusion matrix for pass/fail, worst
  disagreements) as harness output.

### Deliverables
- `src/test/.../evaluation/ThresholdTuningTest.kt` (sweep + report; can be run manually).
- Tuned defaults in `EvaluationConfig`.

### Acceptance Criteria
- Per-axis rank correlation ≥ 0.85; pass/fail agreement ≥ 80% on the corpus.
- Every threshold in `EvaluationConfig` has a comment citing the corpus result that justifies it.

---

## 5.3 Regression gate in CI

### Tasks
- [ ] Promote `CalibrationHarnessTest` from "report only" (Phase 1) to a **hard gate**: build fails if
  agreement drops below the locked thresholds.
- [ ] Add latency assertions (text < 50 ms, hybrid < 300 ms) as part of the gate where feasible.

### Deliverables
- CI-enforced `CalibrationHarnessTest` + latency checks.

### Acceptance Criteria
- A deliberate regression (e.g., reverting to char-Levenshtein) fails the gate.

---

## 5.4 Robustness & graceful degradation audit

**Problem addressed**: §2.1, §3.5 — real-world mic/device variability.

### Tasks
- [ ] Verify behavior when: on-device STT model missing (existing fallback in
  `SpeechRecognitionService`), `AudioRecord` unavailable, permission denied mid-session, very noisy input,
  extremely short/long recitation.
- [ ] Ensure each failure routes to a clear user message and a sensible partial assessment (e.g., "completeness
  + correctness scored; pronunciation not assessed — noisy audio"), never a crash or a misleading low score.
- [ ] On-device-only telemetry of low-agreement / low-confidence cases (no network) to grow the corpus over
  time.

### Deliverables
- Robustness test matrix; updated error/empty states in view models.

### Acceptance Criteria
- All failure modes produce a clear message + valid partial `RecitationAssessment`.

---

## 5.5 (Optional / Stretch) Swara & pitch accuracy

**Why**: Correct Vedic Gayatri recitation has pitch accents (udātta / anudātta / svarita). None of the prior
phases assess pitch — the highest-fidelity dimension of "correct recitation."

### Tasks
- [ ] Extract fundamental frequency (f0) contour from the learner audio (autocorrelation / YIN) and from the
  benchmark per-word segments.
- [ ] Compare normalized pitch contours (DTW on f0, speaker-pitch-normalized) → a **swara accuracy** sub-score
  and a "rising where it should fall" style hint.
- [ ] Gate behind an "Advanced / Vedic mode" toggle; default off (most learners start with phonemes first).

### Deliverables
- `data/signal/PitchExtractor.kt`
- `domain/evaluation/acoustic/SwaraScorer.kt`
- Advanced-mode toggle + UI.

### Acceptance Criteria
- For samples labeled with correct vs flat/incorrect pitch, swara score discriminates them.
- Feature is opt-in and does not affect default scoring.

---

## Exit Criteria

| Criterion | Metric |
|---|---|
| Corpus | Audio + human labels across all axes/error classes |
| Tuned | Per-axis correlation ≥ 0.85; pass/fail agreement ≥ 80%; thresholds justified |
| CI gate | Regressions fail the build; latency checked |
| Robust | Every failure mode → clear message + valid partial assessment |
| Swara (optional) | Opt-in pitch scoring discriminates correct vs incorrect accent |

---

## Closing note

After Phase 5 the engine will: (1) score the three goals **separately** and **explainably**; (2) judge
pronunciation from **audio**, not dictation text; (3) give **syllable-level, audio-linked** coaching;
(4) **adapt** to the learner; and (5) be **provably calibrated** against human judgment and guarded in CI.
That is the difference between a word-matching checker and a tool that actually teaches Gayatri Mantra
recitation.
