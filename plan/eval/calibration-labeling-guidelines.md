# Calibration Corpus — Labeling Guidelines

**Scope**: How to label cases in `app/src/test/resources/eval/corpus.json`, the golden
corpus that calibrates and gates the evaluation engine (`CalibrationHarnessTest`,
`ThresholdTuningTest`). Read this before adding or relabeling a case.

> **Privacy / demo rule**: all corpus data lives in-repo as test resources only. No
> network, no real user recordings. Audio fixtures (when added) are synthetic or
> consented demo clips committed under `app/src/test/resources/eval/audio/`.

---

## 1. The three axes are independent — label them separately

The engine scores three *separable* axes. Do **not** let one bleed into another.

| Axis | What it judges | What it must **ignore** |
|---|---|---|
| **Correctness** | The words that were actually spoken: right words, right order, no swaps/substitutions/extras. | Words that were **omitted** (that is completeness). A recitation that drops a word but says everything else correctly is *fully correct*. |
| **Completeness** | How much of the mantra (words & padas) was recited at all. | How well it was pronounced or ordered. |
| **Pronunciation** | Phonetic fidelity of each recited syllable (aspiration, retroflex/dental, visarga, anusvara, vowel length, pacing). | Missing or swapped words. |

**Common mistake (fixed in Phase 5):** labeling a missing-word case with reduced
*correctness*. Missing words are a **completeness** deduction only; correctness stays
high. Example — `one_word_missing` is `correctness 1.0`, `completeness 0.92`.

---

## 2. Field-by-field

- `id` — stable, unique, snake_case.
- `errorClass` — the single error class the case exercises. The gate asserts the
  corpus covers: `clean`, `missing-word`, `missing-pada`, `swap`, `substitution`,
  `extra`, `de-aspiration`, `retroflex`, `vowel-length`, `visarga`, `gibberish`.
- `transcription` — the raw STT-style text fed to the engine (Latin, Devanagari, or
  mixed). For acoustic-only cases this is the text the dictation engine *would*
  return (usually the **correct** spelling — that is the whole point).
- `audioAsset` — path under `eval/audio/` for the recorded sample, or `null`. Required
  for acoustic error classes.
- `humanCorrectness` / `humanCompleteness` / `humanPronunciation` — `0.0..1.0`, the
  judgment of someone who knows correct recitation (see §3).
- `expectedPass` — the human verdict on whether this attempt should pass overall.
- `pronunciationNeedsAudio` — **the key honesty flag**. `true` when the pronunciation
  defect is only audible and the *text* path cannot see it (the dictation engine
  auto-corrects the spelling, or the defect is a Vedic distinction STT does not
  transcribe: visarga, anusvara, retroflex/dental, aspiration, vowel length, pitch).
  These cases are **excluded from the text-path pronunciation and pass metrics** so
  the CI gate never asks the text path to detect something only audio can. They are
  retained to validate the *acoustic* path (Phase 3) once audio fixtures run.

---

## 3. Scoring rubric (anchors)

Use these anchors so labels are reproducible across labelers:

- **1.0** — indistinguishable from the benchmark on this axis.
- **0.9** — a single minor slip a teacher would barely note.
- **0.8** — clearly noticeable but the meaning/structure is intact.
- **0.6–0.7** — a real defect a learner must fix (e.g. dropped aspiration on `bh`).
- **0.3–0.5** — major errors (several wrong words, a transposed pada).
- **0.0** — unrelated speech / nothing usable on this axis.

`expectedPass` follows the engine's documented rule: pass requires the recitation to
be sufficiently **complete** AND **correct** AND clear the overall sensitivity gate.
A beautiful-sounding recitation that drops half the mantra still **fails**.

---

## 4. Keep the corpus honest, not flattering

- Add cases the engine gets **wrong**, not just easy wins — that is how the gate
  catches regressions and how future phases earn their keep.
- Never tweak a human label just to make the gate green. If the engine genuinely
  disagrees with a correct human label on the *text* path because the signal is
  acoustic, set `pronunciationNeedsAudio = true` — do not inflate the label.
- When you add a case, run `ThresholdTuningTest` (re-tune if defaults shift) and
  `CalibrationHarnessTest` (the gate) locally before committing.
