# Phase 1: Foundations & Three-Axis Model

**Duration**: ~3 days
**Goal**: Remove the structural blockers (duplicated reference text, dead config, single-scalar score,
untested core) so later phases can move fast. Introduce the **three-axis result model**
(Correctness / Completeness / Pronunciation) and a **calibration harness** — *without* changing scoring
behavior the user sees yet.

**Depends on**: nothing (pure refactor + additive model).
**Risk**: Low. No new algorithms; existing behavior preserved behind a compatibility shim.

---

## 1.1 Single-source-of-truth reference model

**Problem addressed**: §2.6 — reference text is duplicated in `MantraEvaluator.REFERENCE_WORDS`,
`DevanagariTransliterator.directReplacements`, `MantraDisplay.kt`, `DemoDataSeeder.kt`, and the plan itself
already diverges (13 vs 14 words).

### Tasks
- [ ] Create a `GayatriMantra` reference object in `domain/evaluation/reference/` describing the mantra ONCE:
  ```kotlin
  data class MantraWord(
      val index: Int,
      val devanagari: String,        // "तत्सवितुर्"
      val transliteration: String,   // "tatsavitur"  (canonical)
      val syllables: List<String>,   // ["tat","sa","vi","tur"]  (for syllable-level feedback)
      val pada: Int,                  // 1..4  (which line of the mantra)
      val benchmarkStartMs: Long,     // from benchmark_timestamps.json
      val benchmarkEndMs: Long
  )

  object GayatriMantra {
      val words: List<MantraWord>          // the 13 canonical words
      val padas: List<IntRange>            // word-index ranges per line
      val devanagariText: String
  }
  ```
- [ ] Migrate `MantraEvaluator.REFERENCE_WORDS`, `MantraDisplay.kt`, `DemoDataSeeder.kt`, and
  `GetWordMasteryUseCase`/`GetChildWeakWordsUseCase` to read from `GayatriMantra`.
- [ ] Load benchmark timestamps from `assets/benchmark_timestamps.json` into the reference (keep parsing in
  data layer, expose plain values to domain).

### Deliverables
- `domain/evaluation/reference/MantraWord.kt`
- `domain/evaluation/reference/GayatriMantra.kt`
- Updated callers (no duplicated literal word lists anywhere).

### Acceptance Criteria
- A grep for the literal `"prachodayat"` / `"tatsavitur"` word lists returns exactly one definition site.
- All existing tests still pass unchanged.

---

## 1.2 Three-axis result model (additive, non-breaking)

**Problem addressed**: §2.3 — the three goals are collapsed into one scalar.

### Tasks
- [ ] Introduce a richer result type alongside (not replacing) `MantraEvaluation`:
  ```kotlin
  data class AxisScore(val score: Float, val band: ScoreBand)   // 0..1 + Excellent/Good/NeedsWork
  enum class ScoreBand { EXCELLENT, GOOD, NEEDS_WORK, POOR }

  data class RecitationAssessment(
      val correctness: AxisScore,    // right words / order / no swaps-extras
      val completeness: AxisScore,   // fraction of words & padas actually recited
      val pronunciation: AxisScore,  // phonetic fidelity (text-derived for now; acoustic in Phase 3)
      val overall: AxisScore,        // documented weighted fusion of the three
      val wordResults: List<WordResult>,
      val padaResults: List<PadaResult>,   // per-line completeness/quality
      val recitationTimeMs: Long
  )
  ```
- [ ] In Phase 1, compute each axis from **existing** signals so behavior is unchanged:
  - `completeness` = (#reference words aligned to a spoken word) / 13, plus per-pada coverage.
  - `correctness` = sequence quality from current alignment (penalize swaps/extras/missing).
  - `pronunciation` = current mean word similarity (placeholder until Phase 2/3).
  - `overall` = sensitivity-weighted blend that reproduces today's `isCorrect` at default settings.
- [ ] Keep `MantraEvaluator.evaluate()` returning `MantraEvaluation`; add `assess()` returning
  `RecitationAssessment`. `MantraEvaluation.overallScore`/`isCorrect` become thin wrappers over `assess()`.

### Deliverables
- `domain/evaluation/model/RecitationAssessment.kt`, `AxisScore.kt`, `PadaResult.kt`, `ScoreBand.kt`
- `MantraEvaluator.assess(...)` + compatibility shim.

### Acceptance Criteria
- For every case in `MantraEvaluatorTest`, `assess(...).overall.score` equals the old `overallScore`
  (± epsilon) at default sensitivity → **proven no behavioral regression**.
- New assertions verify the three axes are independently populated (e.g., "all words present but low
  pronunciation" yields high completeness, low pronunciation).

---

## 1.3 Wire `recitationSensitivity` + kill magic numbers

**Problem addressed**: §2.4, §2.8 — hardcoded `0.85` const, dead `recitationSensitivity`, duplicated literals.

### Tasks
- [ ] Create `domain/evaluation/EvaluationConfig.kt` holding all thresholds and axis weights as a data class
  with documented defaults (correctness/completeness/pronunciation weights, band cutoffs, swap threshold,
  feedback threshold).
- [ ] Plumb `UserPreferencesDataStore.recitationSensitivity` → `EvaluationConfig` → `MantraEvaluator.assess()`
  via `EvaluateRecitationUseCase`. Sensitivity should shift the **pass band**, not silently no-op.
- [ ] Replace duplicated `0.85f` literals in `PracticeScreen`, `WordFeedbackGrid`, `FeedbackBottomSheet`,
  `WordMasteryGrid` with references to a single presentation-facing band mapper.

### Deliverables
- `domain/evaluation/EvaluationConfig.kt`
- Updated `EvaluateRecitationUseCase`, view models, and the four UI files.

### Acceptance Criteria
- Changing the sensitivity slider in Settings measurably moves the pass/fail boundary in a test.
- Zero literal `0.85` magic numbers remain in `presentation/` (enforced by a lint/grep check).

---

## 1.4 Isolated tests for the untested core

**Problem addressed**: §2.10 — `LevenshteinAligner` and `DevanagariTransliterator` have no direct tests.

### Tasks
- [ ] `LevenshteinAlignerTest`: char distance, normalized similarity (incl. short-word edge cases),
  alignment of insert/delete/substitute/reorder scenarios, empty inputs.
- [ ] `DevanagariTransliteratorTest`: each vowel/matra/consonant, virama, anusvara, visarga, the full mantra,
  and the hardcoded overrides (so regressions surface when Phase 2 rewrites it).

### Deliverables
- `src/test/.../evaluation/LevenshteinAlignerTest.kt`
- `src/test/.../evaluation/DevanagariTransliteratorTest.kt`

### Acceptance Criteria
- Both classes hit ≥ 90% line coverage in isolation.

---

## 1.5 Calibration corpus harness (foundation for all later validation)

**Problem addressed**: §2.10 — no proof the evaluator agrees with human judgment.

### Tasks
- [ ] Define a labeled-case format:
  ```kotlin
  data class CalibrationCase(
      val id: String,
      val transcription: String,        // raw STT text (Phase 1–2)
      val audioAsset: String?,          // optional recorded sample (Phase 3+)
      val humanCorrectness: Float,      // 0..1 human label
      val humanCompleteness: Float,
      val humanPronunciation: Float,
      val expectedPass: Boolean
  )
  ```
- [ ] Seed an initial corpus (JSON in `src/test/resources/eval/corpus.json`) covering: perfect, 1-word
  missing, 2-word missing, whole-pada missing, swapped words, extra words, mispronounced-but-right-words,
  gibberish, Devanagari input, mixed script, near-miss phonetics.
- [ ] Write `CalibrationHarnessTest` that runs the engine over the corpus and reports per-axis agreement
  (rank correlation + pass/fail agreement). In Phase 1 it only **reports**; Phase 5 turns it into a CI gate.

### Deliverables
- `src/test/resources/eval/corpus.json`
- `src/test/.../evaluation/CalibrationCase.kt`, `CalibrationHarnessTest.kt`

### Acceptance Criteria
- Harness runs and prints a baseline agreement report for the current engine (this is the "before" number we
  will improve against in Phases 2–3).

---

## Exit Criteria

| Criterion | Metric |
|---|---|
| Reference de-duplicated | One definition of mantra words; all callers migrated |
| Three-axis model live | `assess()` returns 3 independent axes; `overall` matches legacy score |
| Sensitivity wired | Slider changes pass boundary; no `0.85` magic numbers in UI |
| Core tested | Aligner + transliterator ≥ 90% isolated coverage |
| Calibration baseline | Harness prints per-axis human-agreement baseline |
| No regressions | Entire existing test suite green |
