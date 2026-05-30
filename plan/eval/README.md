# Evaluation Engine — Critical Assessment & Improvement Plan

**Scope**: Re-design of the Gayatri Mantra recitation evaluation engine in `domain/evaluation/`.
**Core goal**: Help users *learn and improve* their recitation along three explicit, separable axes:

1. **Correctness** — right words, right order, right sandhi (no swaps/extras/substitutions).
2. **Completeness** — every word/pada of the mantra was actually recited.
3. **Pronunciation** — phonetic fidelity of each syllable (aspiration, retroflex/dental, visarga, anusvara, vowel length).

This folder holds a multi-phased plan to evolve the engine from its current state into a teaching-grade
assessment system. Read this README first, then the phase files in order.

---

## 1. What exists today (honest summary)

| Component | File | Role |
|---|---|---|
| `MantraEvaluator` | `domain/evaluation/MantraEvaluator.kt` | Orchestrates scoring; mean of 13 word scores; 0.85 gate |
| `LevenshteinAligner` | `domain/evaluation/LevenshteinAligner.kt` | Char-level edit distance → word similarity → word-level weighted alignment |
| `TextNormalizer` | `domain/evaluation/TextNormalizer.kt` | NFC → transliterate → lowercase → strip punct → compound fixups → phonetic map |
| `DevanagariTransliterator` | `domain/evaluation/DevanagariTransliterator.kt` | Devanagari → Latin, with hardcoded whole-word overrides |
| `PhoneticMap` | `domain/evaluation/PhoneticMap.kt` | ~12 hardcoded STT-misrecognition → canonical entries |
| `ImprovementSuggestionEngine` | `domain/evaluation/ImprovementSuggestionEngine.kt` | Static per-word tip strings; aggregates weak words |
| Models | `WordResult.kt`, `WordFeedback.kt`, `MantraEvaluation.kt` | Single scalar score + per-word enum feedback |
| STT | `data/speech/SpeechRecognitionService.kt` | Android `SpeechRecognizer`, `hi-IN` free-form, on-device preferred |

**What it does well**
- Clean, pure-Kotlin, testable domain layer (no Android deps) — good architecture to build on.
- Dependency-free two-tier Levenshtein handles insertions/deletions/substitutions in one pass.
- Devanagari transliteration + phonetic map normalize the most common observed STT outputs.

---

## 2. Why the current implementation is not good enough

> **Bottom line: the engine measures the *text a dictation recognizer guessed*, not *how the user actually
> spoke*. For an app whose headline goal is pronunciation, that is the wrong instrument.** It is a reasonable
> "did they say roughly the right words" checker, but it cannot teach pronunciation, and it conflates the
> three goals into one number. Below are the concrete, ranked problems.

### 2.1 (Architectural) Free-form STT text is a poor proxy for pronunciation
`SpeechRecognizer` in `LANGUAGE_MODEL_FREE_FORM` / `hi-IN` is a **dictation** engine. Its language model
*snaps* audio to the nearest plausible Hindi dictionary words. Consequences:
- A **mispronounced** word is often auto-corrected back to the correct spelling → the error is invisible to a
  text comparison. The user gets "correct" while pronouncing badly.
- Sanskrit/Vedic phonemes the LM doesn't expect (visarga `ः`, anusvara `ं`, retroflex vs dental, aspiration
  `bh/dh/gh`, vowel length, pluta elongation, swara pitch accents) are **not faithfully transcribed** — yet
  these are exactly what correct recitation requires.
- **Character edit distance on the resulting Latin string ≠ phonetic distance.** It mostly measures spelling
  resemblance of the recognizer's best guess, mixing recognizer error with user error.

### 2.2 The similarity metric is phonetically blind and length-biased
`wordSimilarity = 1 - editDistance/maxLength` (`LevenshteinAligner.kt:56`):
- Treats `bh→b` (a tiny, common de-aspiration slip) the same as `k→z` (a totally wrong consonant).
- **Length bias**: for `"yo"` (2 chars) one wrong char = 0.5; for `"prachodayat"` (11 chars) one wrong char =
  0.91. Short sacred syllables (`om`, `yo`, `nah`) live permanently on a scoring knife-edge while long words
  are forgiven. The metric is not comparable across words.

### 2.3 The three goals are collapsed into one scalar
The user wants correctness, completeness, and pronunciation as *distinct* feedback. Today there is a single
`overallScore = mean(13 word scores)` (`MantraEvaluator.kt:110`). A learner cannot tell **which** dimension
they failed. Completeness is not actually measured — it only *emerges* as an accident of the mean.

### 2.4 The scoring model & 0.85 gate are arbitrary
- **Equal weighting**: a slip on the 2-letter `"yo"` costs the same 1/13 as butchering `"prachodayat"`.
- **The gate is an accident of 13**: exactly **1** missing word = 12/13 = 0.923 → "correct"; **2** missing =
  11/13 = 0.846 → "incorrect" (this is literally asserted in `MantraEvaluatorTest`). That is not a pedagogical
  decision; it is a side effect of word count.
- **The mean hides distribution**: 13 words all at 0.84 → "incorrect" with no identifiable culprit; 12 perfect
  + 1 zero → "correct." Neither gives the learner a useful signal.

### 2.5 Order/sequence handling is hacky
`SWAPPED` is detected by "does this word resemble *any other* reference slot ≥ 0.85"
(`MantraEvaluator.kt:99`). There is no real sequence model, no handling of repeated/adjacent-similar segments,
no per-pada (line) structure — even though the mantra is taught as 4 padas.

### 2.6 Normalization is brittle, hand-patched, and self-contradicting
- `PhoneticMap` (12 entries) and `DevanagariTransliterator.directReplacements` hardcode specific observed
  outputs (`"yona"→"yo nah"`, `"tatsaviturvarenyam"→…`). Anything not in the table falls through to raw
  Levenshtein. These are symptom patches, not a phonetic model.
- The transliterator **collapses** retroflex/dental (`ट→t`, `त→t`) and is inconsistent about aspiration —
  destroying the very distinctions pronunciation feedback needs.
- The plan/code already diverged: `phase-2-speech-and-evaluation.md` lists **14** words (`"tat","savitur"`)
  while the engine uses **13** (`"tatsavitur"`). Reference text is duplicated in `MantraEvaluator`,
  `DevanagariTransliterator`, `MantraDisplay`, and `DemoDataSeeder` — a correctness hazard.

### 2.7 Available signal is thrown away
- STT `confidence` is captured (`SpeechRecognitionService.kt:112`) but **never used**.
- N-best alternatives (`EXTRA_MAX_RESULTS`) are not requested; only the 1-best string is used — the correct
  word may sit in hypothesis #2.
- The **benchmark audio + per-word timestamps** (`assets/benchmark_timestamps.json`, `WordTimestamp.kt`)
  already exist but are used only for playback highlighting, not for pronunciation comparison.

### 2.8 Dead config & magic numbers
- `recitationSensitivity` (default 0.85) exists in DataStore/Settings but is **never wired** into the engine;
  `CORRECT_THRESHOLD` is a hardcoded `private const`.
- `0.85f` is duplicated literally across `PracticeScreen`, `WordFeedbackGrid`, `FeedbackBottomSheet`,
  `WordMasteryGrid` — violating the project's own "no magic numbers" rule.

### 2.9 Feedback is generic and not actionable
`WordFeedback` enums map to static strings ("Focus on clearer pronunciation"). There is no syllable-level
localization ("you dropped the aspiration on *bh*"), no contrastive guidance, and no link to the reference
audio segment for the failed word (despite timestamps existing).

### 2.10 Test gaps
`LevenshteinAligner` and `DevanagariTransliterator` have **no isolated unit tests** (covered only
transitively). There is **no calibration corpus** proving engine scores correlate with *human* judgment — so
"correctness" of the evaluator itself is unverified.

---

## 3. Recommended target architecture (the strategic decision)

We considered three directions:

- **(A) Keep text-only, make it smarter.** Cheap, on-device, low risk. Phoneme-aware distance, per-axis
  scoring, n-best/confidence fusion. *Good enough for correctness + completeness + coarse pronunciation.*
- **(B) Go fully acoustic.** Forced-alignment + Goodness-of-Pronunciation (GOP) needs a Sanskrit/Hindi phoneme
  acoustic model on device — heavy, no good off-the-shelf model, high risk.
- **(C) Hybrid (RECOMMENDED).** Use **STT text** for *correctness + completeness + sequence* (it is decent at
  "which words, what order"); use **on-device acoustic DTW against the existing benchmark audio** for
  *pronunciation* fidelity and rhythm. Combine into **three independent scores**.

**We recommend (C), delivered incrementally.** It maps 1:1 to the user's three goals, runs fully on-device,
adds no large ML model, and *reuses the benchmark audio + word timestamps the app already ships*. The text
engine is hardened first (immediate wins, zero new infra), then the acoustic pronunciation layer is added,
then the two are fused and personalized.

```
                      ┌─────────────────────────── EvaluationEngine ───────────────────────────┐
  AudioRecord (PCM) ──┤                                                                          │
        │             │   ┌── Acoustic path ──┐   MFCC ─ per-word DTW vs benchmark segments ─┐  │
        │             │   │                    │                                              │  │
        ▼             │   │                    └──────────────► PRONUNCIATION score ──────────┤  │
  SpeechRecognizer ───┼──►│   Text path: n-best + confidence                                  │  │
   (hi-IN, n-best)    │   │     → TextNormalizer → phoneme tokens                              │  │
                      │   │     → phoneme-weighted alignment ──► COMPLETENESS + SEQUENCE ──────┤  │
                      │   └────────────────────────────────────────────────────────────────┐ │  │
                      │                                              fuse (sensitivity-tuned) ▼ ▼  │
                      │                                        RecitationAssessment(3 axes + tips) │
                      └──────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Guiding principles

- **Separate the three axes end-to-end** — never collapse to one number until the very last (and keep the
  sub-scores for feedback).
- **Phonetics over spelling** — operate on phoneme/syllable tokens with feature-weighted distances, not raw
  Latin characters.
- **Reference is single-source-of-truth** — one `GayatriMantra` domain object (words, padas, Devanagari,
  syllables, timestamps); everything else reads from it.
- **Reuse what we ship** — benchmark audio + timestamps drive both pronunciation scoring *and* "tap to hear"
  feedback.
- **Calibrate, don't guess** — every threshold is justified against a labeled golden corpus and gated in CI.
- **On-device, private, fast** — no network; full evaluation < 300 ms after audio stops (text < 50 ms).
- **Backwards-compatible migration** — keep `MantraEvaluation.overallScore` working while the new
  `RecitationAssessment` is introduced behind it.

---

## 5. Phase map

| Phase | Title | Outcome | Risk |
|---|---|---|---|
| [1](phase-1-foundations-and-three-axis-model.md) | Foundations & three-axis model | Single-source reference, three-axis result type, sensitivity wired in, isolated tests, calibration harness | Low |
| [2](phase-2-phonetic-text-scoring.md) | Phonetically-aware text scoring | Phoneme tokens + feature-weighted distance, per-axis scores, n-best/confidence fusion | Medium |
| [3](phase-3-acoustic-pronunciation-layer.md) | Acoustic pronunciation layer | MFCC + per-word DTW vs benchmark → real pronunciation score, "tap to hear" | High |
| [4](phase-4-feedback-personalization-mastery.md) | Feedback, personalization & mastery | Syllable-level actionable tips, adaptive thresholds, per-axis progress | Medium |
| [5](phase-5-calibration-validation-advanced.md) | Calibration, validation & advanced | Threshold tuning vs corpus, CI gate, optional swara/pitch contour | Medium |

**Sequencing**: Phases 1→2 deliver most learning value at low risk and can ship independently. Phase 3 is the
architectural leap (real pronunciation). Phases 4–5 compound the value. Each phase is independently shippable.

---

## 6. Success metrics (how we know it actually improved)

| Metric | Target |
|---|---|
| Agreement with human grades (per-axis), on golden corpus | ≥ 0.85 rank correlation; ≥ 80% pass/fail agreement |
| Pronunciation discrimination | Engine separates "correct words, bad pronunciation" from "correct" (impossible today) |
| Per-axis explainability | Every result tells the user which of the 3 axes failed and where |
| Actionability | Each weak word links to its reference audio segment + a specific syllable tip |
| Latency | Text path < 50 ms; full hybrid < 300 ms on a mid-range device |
| Test coverage | `domain/evaluation/` ≥ 90%; isolated tests for aligner, transliterator, DTW; golden-corpus regression in CI |
| No dead config | `recitationSensitivity` measurably changes results; zero duplicated `0.85` literals |
