# Phase 2: Phonetically-Aware Text Scoring

**Duration**: ~4 days
**Goal**: Replace spelling-based character Levenshtein with **phoneme/syllable-aware** scoring, compute the
three axes from real linguistic structure, and stop throwing away STT signal (n-best + confidence). This is
the biggest improvement achievable **without** new audio infrastructure.

**Depends on**: Phase 1 (reference model, three-axis result, config, calibration harness).
**Risk**: Medium. New distance metric + tokenizer; guarded by the Phase-1 calibration corpus.

---

## 2.1 Phoneme / syllable tokenization

**Problem addressed**: §2.2 — char edit distance is phonetically blind and length-biased.

### Tasks
- [ ] Add a transliteration scheme that preserves phonetic distinctions (use **SLP1** or **ISO-15919/IAST**
  internally — they encode aspiration, retroflex vs dental, long vowels, visarga, anusvara as distinct
  tokens). Rewrite `DevanagariTransliterator` to emit these phoneme tokens instead of lossy Latin
  (`ट→ṭ` vs `त→t`, `भ→bh` distinct from `ब→b`, `ः→ḥ`, `ं→ṃ`, long `ī/ū`).
- [ ] Add a `PhonemeTokenizer` that converts both reference and (normalized) STT output into ordered phoneme
  token lists, grouped into syllables.
- [ ] Keep the existing lossy Latin transliteration available for display only.

### Deliverables
- `domain/evaluation/phonetics/PhonemeTokenizer.kt`
- `domain/evaluation/phonetics/PhonemeScheme.kt` (SLP1/IAST tables)
- Rewritten `DevanagariTransliterator.kt` (phoneme-faithful), with the Phase-1 regression test guarding it.

### Acceptance Criteria
- Reference words tokenize into correct phoneme sequences (`dhimahi → dh i m a h i`).
- Retroflex/dental and aspirated/unaspirated pairs are distinct tokens.

---

## 2.2 Feature-weighted phonetic distance

**Problem addressed**: §2.2 — `bh→b` must cost less than `k→z`; short words must not be over-penalized.

### Tasks
- [ ] Build a **phonetic feature substitution-cost matrix**: each phoneme has features (voicing, aspiration,
  place, manner, vowel length, nasality). Substitution cost = weighted feature mismatch (small for
  near-neighbors like aspiration loss / retroflex↔dental, large for unrelated phonemes).
- [ ] Implement `PhoneticDistance.wordSimilarity(refTokens, spokenTokens)` as a weighted edit distance over
  phoneme tokens (reuse the DP structure already in `LevenshteinAligner`, swap unit cost for feature cost).
- [ ] **Fix length bias**: normalize by summed reference token weights (or phoneme count with a min-length
  floor) instead of raw `maxLength`, so `om`/`yo`/`nah` are scored comparably to long words.
- [ ] Keep `LevenshteinAligner` for word-sequence alignment, but feed it the new phonetic similarity.

### Deliverables
- `domain/evaluation/phonetics/PhoneticFeatures.kt`
- `domain/evaluation/phonetics/PhoneticDistance.kt`
- Updated `LevenshteinAligner` substitution cost source.

### Acceptance Criteria
- `sim("bhargo","bargo")` (dropped aspiration) > `sim("bhargo","kargo")` (wrong consonant).
- Short-word penalty is comparable to long-word penalty for an equivalent error class (verified in tests).
- Calibration harness pronunciation-axis agreement improves vs Phase-1 baseline.

---

## 2.3 Per-axis scoring from structure

**Problem addressed**: §2.3, §2.4, §2.5 — collapse to one number; arbitrary mean & 0.85 gate; weak sequence.

### Tasks
- [ ] **Completeness** = coverage of reference words (and per-pada coverage) actually aligned, independent of
  pronunciation quality. Missing a whole pada is penalized more than missing one scattered word.
- [ ] **Correctness/sequence** = order fidelity: penalize swaps (proper detection via alignment crossing, not
  the "matches any slot" hack), extra words, and substitutions of wrong-but-real words. Use the per-pada
  structure so "right words wrong order within a pada" is distinguished from "padas out of order."
- [ ] **Pronunciation** = aggregate phonetic similarity of aligned words, **weighted by word importance**
  (e.g., syllable count / liturgical weight) instead of a flat mean — replaces §2.4's equal weighting.
- [ ] **Overall** = documented, sensitivity-tuned fusion (e.g., gate on completeness AND correctness, then
  grade pronunciation), replacing the accidental 13-word 0.85 gate with an explicit, explainable rule.

### Deliverables
- `domain/evaluation/scoring/CompletenessScorer.kt`
- `domain/evaluation/scoring/SequenceScorer.kt`
- `domain/evaluation/scoring/PronunciationScorer.kt`
- `MantraEvaluator.assess()` rewired to compose the three scorers.

### Acceptance Criteria
- "All 13 words, mispronounced" → high completeness, high correctness, low pronunciation (today: impossible
  to express).
- "12 perfect + 1 missing" vs "all present but uniformly weak" produce *different, sensible* axis profiles.
- The arbitrary "1 missing = pass, 2 missing = fail" artifact is gone; pass rule is explicit in
  `EvaluationConfig`.

---

## 2.4 N-best + confidence fusion

**Problem addressed**: §2.7 — only 1-best transcript is used; confidence ignored.

### Tasks
- [ ] Request multiple hypotheses from STT (`EXTRA_MAX_RESULTS`, read full `RESULTS_RECOGNITION` list +
  `CONFIDENCE_SCORES`). Extend `SpeechResult.Final` to carry `alternatives: List<Pair<String, Float>>`.
- [ ] In `MantraEvaluator`, score each hypothesis and select the **best-aligning** one (or merge per-word: for
  each reference slot, take the best-matching word across hypotheses). Use confidence as a tie-breaker and as
  a soft prior in word scoring.
- [ ] Surface low STT confidence as "unclear audio — try again in a quieter spot" rather than a false low
  pronunciation score.

### Deliverables
- Updated `SpeechResult.kt`, `SpeechRecognitionService.kt` (request n-best).
- `domain/evaluation/HypothesisSelector.kt`.
- Updated view models (`PracticeModeViewModel`, `RecitationViewModel`) to pass alternatives through.

### Acceptance Criteria
- A case where the correct word is in hypothesis #2 now scores correctly (regression test added to corpus).
- Very low confidence routes to an "unclear audio" message, not a pronunciation penalty.

---

## 2.5 Rule-based normalization (retire the hardcoded patches)

**Problem addressed**: §2.6 — `PhoneticMap` and `directReplacements` are brittle symptom patches.

### Tasks
- [ ] Replace most of `PhoneticMap`'s hardcoded entries with rule-based phonetic canonicalization derived from
  the feature model (e.g., long↔short vowel folding, `w↔v`, terminal `aa↔a`, visarga echo `ah↔aha`).
- [ ] Replace `DevanagariTransliterator.directReplacements` whole-word hacks with proper sandhi-aware
  segmentation where feasible; keep a *small* override table only for genuinely irregular STT outputs, clearly
  documented as overrides.

### Deliverables
- Refactored `TextNormalizer.kt`, `PhoneticMap.kt` (rules + minimal overrides).

### Acceptance Criteria
- Removing any single override entry does not collapse a whole-word match (rules cover it).
- `TextNormalizerTest` still passes; new tests cover the rule-based folds.

---

## Exit Criteria

| Criterion | Metric |
|---|---|
| Phonetic scoring | Feature-weighted distance; aspiration/retroflex errors cost < wrong-phoneme errors |
| Length bias fixed | Short and long words penalized comparably for equivalent errors |
| Three real axes | Completeness/correctness/pronunciation computed from structure, not a single mean |
| Explicit pass rule | 0.85-of-13 artifact removed; rule lives in `EvaluationConfig` |
| Signal reuse | N-best + confidence used; "unclear audio" path exists |
| Calibration | Per-axis human agreement improves measurably vs Phase-1 baseline |
| Performance | Text path still < 50 ms |
