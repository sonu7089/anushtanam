# Adding a mantra

Adding a mantra to Anushtanam means adding **one JSON file** and naming it in a second one.
No Kotlin changes. The evaluation engine, the practice plan, the feedback, the progress
screens and the Purashcharana all read whichever mantra they are given.

This document is written to be followed end to end by a person or a coding agent with no
prior knowledge of the codebase.

---

## TL;DR

1. Create `core/domain/src/main/resources/mantras/<id>.json` (format below).
2. Add `"<id>.json"` to the `mantras` array in
   `core/domain/src/main/resources/mantras/catalog.json`.
3. *(Optional)* Drop a reference recitation at
   `core/resources/src/main/res/raw/<id>_benchmark.mp3` and time each word against it.
4. *(Optional)* Point `defaultMantraId` at the new id to make it the mantra the app teaches.
5. Run `.\gradlew.bat :core:domain:test`. A bad definition fails with a message naming the
   file and the field.

---

## 1. Where things live

| Path | What it is |
| --- | --- |
| `core/domain/src/main/resources/mantras/catalog.json` | The index: which definitions to load, and which one the app teaches. |
| `core/domain/src/main/resources/mantras/<id>.json` | One mantra definition. |
| `core/resources/src/main/res/raw/<id>_benchmark.mp3` | Optional reference recitation. |
| `core/resources/src/main/assets/<id>_timestamps.json` | Optional refined word timings. |

These are Java resources on the domain module's classpath, so the same files serve unit
tests and the packaged app. They are read once, at first use, by `BundledMantraCatalog`.

---

## 2. The catalog index

```json
{
  "defaultMantraId": "gayatri",
  "mantras": [
    "gayatri.json"
  ]
}
```

| Field | Required | Meaning |
| --- | --- | --- |
| `defaultMantraId` | yes | The mantra the app teaches, scores and counts japa against. Must match the `id` of one of the loaded definitions. |
| `mantras` | yes | Definition file names, resolved next to `catalog.json`. At least one. |

**The app teaches exactly one mantra at a time, and `defaultMantraId` is how it is chosen.**
Every scorer and use case takes the mantra as a dependency (`@ActiveMantra`), so switching
is a one-line change here — see [Making it the active mantra](#6-making-it-the-active-mantra).

---

## 3. A mantra definition

Here is a complete, minimal example — a short mantra with no reference recording:

```json
{
  "id": "mahamrityunjaya",
  "name": "Mahamrityunjaya Mantra",
  "padaCount": 2,
  "aksharaCount": 32,
  "words": [
    { "devanagari": "ॐ",            "transliteration": "om",         "syllables": ["om"],                    "pada": 1 },
    { "devanagari": "त्र्यम्बकं",      "transliteration": "tryambakam", "syllables": ["tryam", "ba", "kam"],    "pada": 1 },
    { "devanagari": "यजामहे",        "transliteration": "yajamahe",   "syllables": ["ya", "ja", "ma", "he"],  "pada": 1 },
    { "devanagari": "सुगन्धिं",       "transliteration": "sugandhim",  "syllables": ["su", "gan", "dhim"],     "pada": 2 },
    { "devanagari": "पुष्टिवर्धनम्",   "transliteration": "pushtivardhanam", "syllables": ["push", "ti", "var", "dha", "nam"], "pada": 2 }
  ]
}
```

### Top-level fields

| Field | Required | Type | Notes |
| --- | --- | --- | --- |
| `id` | yes | string | Stable machine id, lowercased on read. **Never change it once the mantra has shipped** — it is stored on every recitation session and every Purashcharana vow. |
| `name` | yes | string | English name. Used in logs and diagnostics; user-facing copy lives in `strings.xml`. |
| `padaCount` | yes | int > 0 | Number of padas (lines) the mantra is taught in. |
| `aksharaCount` | yes | int > 0 | Aksharas the mantra is *reckoned* at. See the warning below. |
| `words` | yes | array | The words in recitation order. At least one. |
| `benchmark` | no | object | The reference recitation. See §4. |
| `textRules` | no | object | Mantra-specific normalization of dictation output. See §5. |

Any key the reader does not recognise is ignored, so you can annotate a file with e.g.
`"_source": "..."` — JSON has no comments, and this is the convention the shipped
definitions use.

> **`aksharaCount` is not derived from `words`, and that is deliberate.**
> A Purashcharana is one lakh japa *per akshara* — 24 × 1,00,000 = 24,00,000 japa for the
> Gayatri. The Gayatri is reckoned at 24 because that is the metre of the Savitri verse
> proper; the pranava (ॐ) and the vyahritis are recited with it but are not counted. Put
> the number the tradition uses here, not a syllable tally. Getting it wrong changes the
> length of every vow taken for this mantra.

### Word fields

| Field | Required | Type | Notes |
| --- | --- | --- | --- |
| `devanagari` | yes | string | The word in Devanagari. Shown on the practice screen. |
| `transliteration` | yes | string | Canonical Latin spelling, lowercased on read. **Must be unique within the mantra** — per-word scores are keyed by it. |
| `syllables` | yes | array of strings | Ordered syllables. Drives syllable-level feedback ("the `bhar` in `bhargo`") and weights the word's importance in the pronunciation score. |
| `pada` | yes | int, `1..padaCount` | The line this word belongs to. |
| `benchmarkStartMs` | no | int ≥ 0 | Start of this word in the recording, in milliseconds. |
| `benchmarkEndMs` | no | int ≥ 0 | End of this word. Must be ≥ start. |

Rules the reader enforces:

- **Word order is array order.** Indices are assigned from position; a definition cannot
  claim an order it does not have.
- **Padas must be consecutive and complete.** All of pada 1's words, then all of pada 2's,
  and so on, with every pada from `1` to `padaCount` carrying at least one word.
- **Transliterations must be distinct.** If a mantra genuinely repeats a word, disambiguate
  it (`yo`, `yo-2`); scores are stored per transliteration and would otherwise collide.

### Writing good transliterations

The evaluator compares **phonemes**, not letters, and normalizes dictation output first
(long vowels fold to short, `w` → `v`, a trailing `aha` → `ah`, `aum`/`oom` → `om`). Write
the plain, obvious Latin spelling: `dhimahi`, not `dhīmahi`; `prachodayat`, not
`pracodayāt`. Aspirates keep their `h` (`bh`, `dh`, `ch`), because that is exactly the
distinction the feedback layer teaches.

Split syllables the way you would teach them aloud. They are what the learner is shown when
a word goes wrong, so `["pra", "cho", "da", "yat"]` is right and `["p", "rachodayat"]` is not.

---

## 4. Adding a reference recitation (optional)

Without a recording, a mantra is scored from the transcript alone — correctness,
completeness and text-phonetic pronunciation all work. A recording adds the acoustic
pronunciation comparison, the optional swara (pitch) layer, and "tap a word to hear it".

```json
"benchmark": {
  "audioResourceName": "gayatri_mantra_benchmark",
  "timestampsAsset": "benchmark_timestamps.json",
  "timestampsUnit": "centiseconds"
}
```

| Field | Required | Notes |
| --- | --- | --- |
| `audioResourceName` | yes | Name of the file in `core/resources/src/main/res/raw`, **without extension**. Name it `<id>_benchmark` — `raw/keep.xml` keeps anything matching `*_benchmark` from being stripped by the release resource shrinker. |
| `timestampsAsset` | no | A file in `core/resources/src/main/assets` holding refined per-word timings that override the ones in this definition. |
| `timestampsUnit` | no | `milliseconds` (default) or `centiseconds`. Applies to `timestampsAsset` only; the values in `words` are always milliseconds. |

**If you declare a `benchmark`, every word must have `benchmarkStartMs` and
`benchmarkEndMs`.** Per-word audio scoring needs a window for each word, so a
partly-timed definition is rejected rather than silently half-working. Time all of them or
none of them.

The timings file, when you use one, is an array:

```json
[
  { "wordIndex": 0, "startMs": 0,   "endMs": 334 },
  { "wordIndex": 1, "startMs": 339, "endMs": 428 }
]
```

`wordIndex` is the 0-based position in `words`. A missing or malformed file falls back to
the definition's own timings silently — a bad timing file must never stop a recitation
being scored.

---

## 5. Dictation quirks (optional)

Speech-to-text has habits. General Sanskrit-in-Latin folding is already handled for every
mantra (see `PhoneticMap`). `textRules` is for what is true only of *this* mantra:

```json
"textRules": {
  "compoundReplacements": [
    { "from": "tat savitur", "to": "tatsavitur" },
    { "from": "tatsaviturvarenyam", "to": "tatsavitur varenyam" }
  ],
  "spellingOverrides": {
    "bhuva": "bhuvah",
    "bharago": "bhargo"
  }
}
```

- **`compoundReplacements`** — literal substitutions applied **in order** to the normalized
  transcript before it is split into words. Use them where the engine runs words together
  or splits one apart. Order matters: an earlier replacement can create the text a later
  one matches, and a longer compound must be listed before any prefix of it.
- **`spellingOverrides`** — whole-word fixes, keyed by what was heard (after the general
  folding rules) and mapping to the canonical reference word. Keep this table small; the
  phonetic distance is tolerant, so most near-misses already align without help.

Start with no `textRules` at all. Add entries only when you have watched a real
transcription fail.

---

## 6. Making it the active mantra

The app teaches one mantra at a time. To switch:

```json
{ "defaultMantraId": "mahamrityunjaya", "mantras": ["gayatri.json", "mahamrityunjaya.json"] }
```

That is the whole change. `MantraModule` in `core/data` binds `@ActiveMantra Mantra` to the
catalog's default, and everything downstream — evaluation, the practice plan, word mastery,
the benchmark player, new Purashcharana vows — follows.

Two things to know before you do it:

- **History is not rewritten.** Sessions and vows carry the `mantraId` they were recorded
  with. Per-word analytics filter to the active mantra, so switching hides the old mantra's
  word history rather than mixing it in, and an existing Purashcharana for the old mantra is
  left alone rather than merged into a vow for the new one. That is the intended behaviour.
- **The UI copy does not follow.** Strings such as the practice-screen title and the
  Purashcharana explainer name the Gayatri in `core/resources/.../strings.xml`. Those are
  presentation, not backend, and need editing separately.

---

## 7. Verifying

```powershell
.\gradlew.bat :core:domain:test
```

`BundledMantraCatalogTest` loads every bundled definition, so a broken file fails here
first, with the file and field named:

```
/mantras/mahamrityunjaya.json: 'words[3].pada' is 3 but the mantra declares padaCount 2.
```

Then the full check:

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

`SecondMantraTest` is worth reading if you want to see the whole flow exercised: it defines
a mantra inline, scores it, gets feedback on it and schedules practice for it, without a
line of production Kotlin knowing it exists. If adding a mantra ever comes to need more than
a definition file, that test is where it will show up.

---

## 8. Checklist

- [ ] `core/domain/src/main/resources/mantras/<id>.json` created.
- [ ] `id` is lowercase, stable, and not already used.
- [ ] `aksharaCount` is the number the tradition reckons the mantra at.
- [ ] Words are in recitation order and grouped by pada, `1..padaCount`.
- [ ] Every `transliteration` is distinct and plainly spelled.
- [ ] Syllables read the way you would teach them aloud.
- [ ] If there is a `benchmark`: audio is in `res/raw` named `<id>_benchmark`, and **every**
      word is timed.
- [ ] `<id>.json` is listed in `catalog.json`.
- [ ] `.\gradlew.bat :core:domain:test` passes.
