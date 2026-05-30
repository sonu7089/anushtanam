# Phase 4: Actionable Feedback, Personalization & Mastery

**Duration**: ~4 days
**Goal**: Turn accurate three-axis scores into **learning**. Replace generic enum strings with syllable-level,
contrastive, audio-linked guidance; adapt difficulty to the learner; and track mastery per axis over time so
the app actively coaches improvement.

**Depends on**: Phases 1–3 (three axes, phoneme structure, per-word acoustic + benchmark segments).
**Risk**: Medium — mostly presentation + use-case work; no new heavy algorithms.

---

## 4.1 Syllable-level, contrastive feedback

**Problem addressed**: §2.9 — `WordFeedback` enums map to static, non-actionable strings.

### Tasks
- [x] Generate a **syllable-level diff** for weak words from the Phase-2 phoneme alignment: identify *which
  syllable/feature* was wrong (dropped aspiration on `bh`, dental↔retroflex swap, missing visarga echo,
  shortened long vowel).
- [x] Produce **contrastive** tips: "You said *bargo* — the *bh* needs a breath: *b-h-argo*." Templated from
  the feature that mismatched, not a fixed per-word string.
- [x] Localize all feedback in `strings.xml` (per AGENTS.md: no hardcoded UI strings), with placeholders for
  the detected syllable/feature. Keep `ImprovementSuggestionEngine` as the orchestrator but drive it from the
  new diff instead of the static `WORD_SUGGESTIONS` table.

### Deliverables
- `domain/evaluation/feedback/SyllableDiff.kt`
- `domain/evaluation/feedback/FeedbackComposer.kt`
- Refactored `ImprovementSuggestionEngine.kt`; new `strings.xml` entries.

### Acceptance Criteria
- Feedback names the specific syllable/feature for ≥ the top weak word, not a generic sentence.
- All feedback strings come from `strings.xml`.

---

## 4.2 "Tap to hear" reference segments

**Problem addressed**: §2.7 — benchmark timestamps exist but only drive playback highlighting.

### Tasks
- [x] For each weak word, expose its benchmark segment (`benchmarkStartMs..benchmarkEndMs` from the Phase-1
  reference) so the UI can play *just that word* from `gayatri_mantra_benchmark.mp3`.
- [x] Optionally play back the learner's own recorded segment (Phase 3 buffer) next to the reference for A/B
  comparison.
- [x] Wire into `FeedbackBottomSheet` / `WordFeedbackGrid`: tap a weak word → hear reference (and self).

### Deliverables
- `data/audio/BenchmarkAudioPlayer.kt` (add segment playback API)
- Updated feedback composables.

### Acceptance Criteria
- Tapping a weak word plays exactly that word from the benchmark audio.
- A/B self-vs-reference playback works when an audio buffer exists.

---

## 4.3 Adaptive difficulty & sensitivity

**Problem addressed**: §2.4, §2.8 — one fixed gate for everyone; sensitivity underused.

### Tasks
- [x] Use mastery history (`GetWordMasteryUseCase`) to set a **learner level** (beginner → advanced) that
  shifts `EvaluationConfig` band cutoffs: lenient pronunciation gate for beginners, strict for advanced.
- [x] Combine the user `recitationSensitivity` preference with the adaptive level (explicit override > auto).
- [x] Distinguish "passed for *your level*" from "passed at *mastery* standard" so progress feels earned but
  improvement is always visible.

### Deliverables
- `domain/usecase/DetermineLearnerLevelUseCase.kt`
- Updated `EvaluationConfig` resolution in `EvaluateRecitationUseCase`.

### Acceptance Criteria
- Same recitation passes for a beginner profile but is flagged "needs work on pronunciation" for an advanced
  profile, in a test.

---

## 4.4 Per-axis mastery & spaced repetition

**Problem addressed**: §2.3 — progress tracking is single-score; no targeted practice loop.

### Tasks
- [x] Persist per-axis scores per session (extend `RecitationSession`/entity with completeness, correctness,
  pronunciation; provide a Room migration).
- [x] Compute per-word and per-pada mastery trends; surface the weakest 1–3 items and **schedule** them
  (simple spaced-repetition: resurface words that are weak or overdue).
- [x] Feed weakest items into Practice mode as a focused drill ("practice *dhimahi* and *varenyam* today").

### Deliverables
- Migration + updated `RecitationSessionEntity`, `RecitationSession`, `RecitationDao`.
- `domain/usecase/GetPracticePlanUseCase.kt`
- Updated `domain/usecase/GetWordMasteryUseCase.kt` (per-axis).

### Acceptance Criteria
- Progress screen can show three trend lines (correctness/completeness/pronunciation) over time.
- A focused practice plan returns the learner's current weakest words/padas.

---

## Exit Criteria

| Criterion | Metric |
|---|---|
| Actionable feedback | Names the specific syllable/feature; sourced from `strings.xml` |
| Audio-linked | Tap-to-hear reference (and self) for weak words |
| Adaptive | Difficulty shifts by learner level + sensitivity; verified in tests |
| Per-axis mastery | Sessions store 3 axes; migration in place; trends available |
| Practice loop | Weakest items resurface as a focused drill |
