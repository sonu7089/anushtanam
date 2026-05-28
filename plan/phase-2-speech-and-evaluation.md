# Phase 2: Speech Recognition & Evaluation Engine

**Duration**: ~4 days  
**Goal**: Working speech-to-text integration with a robust client-side evaluation engine that scores recitations word-by-word.

**Depends on**: Phase 1 complete.

---

## 2.1 Speech Recognition Service

### Tasks
- [x] Create `SpeechRecognitionService` wrapper around Android `SpeechRecognizer`
- [x] Configure for **offline/on-device** mode (`EXTRA_PREFER_OFFLINE = true`)
- [x] Set recognition language to Hindi (`hi-IN`) with English fallback (`en-IN`)
- [x] Handle lifecycle: start, partial results, final result, error, stop
- [x] Emit results as `Flow<SpeechResult>`:
  ```kotlin
  sealed interface SpeechResult {
      data class Partial(val text: String) : SpeechResult
      data class Final(val text: String, val confidence: Float) : SpeechResult
      data class Error(val code: Int, val message: String) : SpeechResult
  }
  ```
- [x] Handle permissions: `RECORD_AUDIO` runtime permission request
- [x] Handle edge cases: no speech detected, network unavailable (offline mode), timeout

### Deliverables
- `data/speech/SpeechRecognitionService.kt`
- `data/speech/SpeechResult.kt`
- `presentation/components/PermissionHandler.kt`

### Acceptance Criteria
- STT works fully offline (no network required)
- Hindi speech correctly transcribed
- Partial results stream in real-time
- Graceful error handling for all `SpeechRecognizer.ERROR_*` codes

---

## 2.2 Text Normalization Pipeline

### Tasks
- [x] Create `TextNormalizer` class with the following pipeline:

```
Raw STT Output
    ↓
1. Lowercase
    ↓
2. Remove punctuation & special chars
    ↓
3. Normalize Unicode (NFC normalization)
    ↓
4. Normalize whitespace (collapse multiple spaces)
    ↓
5. Apply phonetic equivalence map
    ↓
6. Apply common STT error corrections
    ↓
Normalized Text
```

- [x] **Phonetic equivalence map** (common STT confusions):
  ```
  "bhoor" → "bhur"
  "bhuva" → "bhuvah"  
  "swaha" → "svah"
  "savitoor" → "savitur"
  "varenyam" → "varenyam"  (already correct)
  "bharago" → "bhargo"
  "dhee" → "dhi"
  "prachodayaat" → "prachodayat"
  "aum" → "om"
  ```
- [x] Handle both Devanagari and transliterated input
- [x] Create Devanagari-to-transliteration converter for uniform comparison

### Deliverables
- `domain/evaluation/TextNormalizer.kt`
- `domain/evaluation/PhoneticMap.kt`
- `domain/evaluation/DevanagariTransliterator.kt`

### Acceptance Criteria
- ≥30 unit tests covering normalization edge cases
- Handles mixed Devanagari/English input
- Idempotent: normalizing already-normalized text produces same result

---

## 2.3 Word-Level Evaluation Engine

### Tasks
- [x] Create `MantraEvaluator` — the core scoring engine
- [x] Define reference mantra as word array:
  ```kotlin
  val REFERENCE_WORDS = listOf(
      "om", "bhur", "bhuvah", "svah",
      "tat", "savitur", "varenyam",
      "bhargo", "devasya", "dhimahi",
      "dhiyo", "yo", "nah", "prachodayat"
  )
  ```
- [x] **Word matching algorithm**:
  1. Normalize both reference and input text
  2. Tokenize into word arrays
  3. Align input words to reference words using **Levenshtein-based alignment**
  4. Score each word pair: `1.0 - (editDistance / maxLength)`
  5. Words with no match = 0.0
  6. Extra words in input = ignored (user may add words)
  7. Missing words = 0.0 score for that position

- [x] **Scoring output**:
  ```kotlin
  data class MantraEvaluation(
      val overallScore: Float,           // mean of word scores
      val wordResults: List<WordResult>, // per-word breakdown
      val isCorrect: Boolean,            // overallScore >= 0.85
      val missingWords: List<String>,    // words user skipped
      val extraWords: List<String>,      // words user added
      val recitationTimeMs: Long
  )
  
  data class WordResult(
      val referenceWord: String,
      val spokenWord: String?,    // null if missing
      val score: Float,           // 0.0 - 1.0
      val feedback: WordFeedback? // improvement tip if score < 0.7
  )
  ```

- [x] **Feedback generation** — per-word improvement tips:
  ```kotlin
  enum class WordFeedback(val message: String) {
      PRONUNCIATION("Focus on clearer pronunciation of this word"),
      MISSING("This word was skipped — try to include it"),
      EXTRA("This word is not part of the mantra"),
      PARTIAL("Close! Try emphasizing the ending syllable"),
      SWAPPED("Words may be in wrong order — check sequence")
  }
  ```

### Deliverables
- `domain/evaluation/MantraEvaluator.kt`
- `domain/evaluation/WordResult.kt`
- `domain/evaluation/MantraEvaluation.kt`
- `domain/evaluation/WordFeedback.kt`
- `domain/evaluation/LevenshteinAligner.kt`

### Acceptance Criteria
- Perfect recitation → score 1.0, `isCorrect = true`
- 1 wrong word out of 14 → score ~0.93, still correct
- 3+ wrong words → `isCorrect = false`
- Missing words correctly identified
- Per-word feedback generated for words below 0.7
- ≥40 unit tests covering all edge cases
- Runs in <50ms on mid-range device (pure computation, no I/O)

---

## 2.4 Benchmark Audio System

### Tasks
- [x] Record/source a clear, correct recitation of Gayatri Mantra (audio file)
- [x] Bundle as `res/raw/gayatri_mantra_benchmark.mp3`
- [x] Create `BenchmarkAudioPlayer` wrapper:
  - Play / Pause / Stop controls
  - Playback progress tracking
  - Proper `MediaPlayer` lifecycle management (release on screen exit)
- [x] Add word-by-word highlight sync (timestamp markers for each word in the audio)

### Deliverables
- `res/raw/gayatri_mantra_benchmark.mp3`
- `data/audio/BenchmarkAudioPlayer.kt`
- `data/audio/WordTimestamp.kt` (word → start/end ms mapping)

### Acceptance Criteria
- Audio plays correctly with play/pause/stop
- No audio leak on screen navigation
- Word highlighting syncs with audio playback

---

## 2.5 Integration: Wire Speech → Evaluation → Storage

### Tasks
- [x] Create `EvaluateRecitationUseCase`:
  1. Receive raw transcription from `SpeechRecognitionService`
  2. Run through `TextNormalizer`
  3. Pass to `MantraEvaluator`
  4. Save `RecitationSessionEntity` via `RecitationRepository`
  5. If in NORMAL mode and `isCorrect`, increment target count
  6. Return `MantraEvaluation` to presentation layer
- [x] Create `IncrementTargetUseCase` — atomic increment of recitation count + completion check

### Deliverables
- `domain/usecase/EvaluateRecitationUseCase.kt`
- `domain/usecase/IncrementTargetUseCase.kt`

### Acceptance Criteria
- End-to-end: speak → transcribe → normalize → evaluate → store → update target
- All operations are atomic (no partial state on error)
- Correct recitations increment target count

---

## Exit Criteria

| Criterion | Metric |
|-----------|--------|
| STT works offline | Transcribes Hindi speech without network |
| Normalization robust | ≥30 test cases passing |
| Evaluation accurate | ≥40 test cases, correct/incorrect classification matches expectations |
| Audio playback works | Play/pause/stop with no leaks |
| End-to-end pipeline | Speak → score → store works |
| Performance | Evaluation completes in <50ms |
