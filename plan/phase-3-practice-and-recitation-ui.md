# Phase 3: Practice Mode & Recitation Mode UI

**Duration**: ~4 days  
**Goal**: Fully functional Practice Mode and Normal Recitation Mode screens with real-time feedback UI.

**Depends on**: Phase 2 complete.

---

## 3.1 Practice Mode Screen

### User Flow
1. User lands on Practice screen
2. Sees the Gayatri Mantra displayed in Devanagari + transliteration
3. Can tap "🔊 Listen" button to play benchmark audio
4. Taps "🎤 Start Practice" to begin recording
5. Speaks the mantra
6. App stops recording (auto or manual)
7. Results appear: per-word color-coded feedback
   - Green = correct (score ≥ 0.85)
   - Yellow = close (score 0.5–0.84)
   - Red = incorrect (score < 0.5)
   - Gray = missing
8. Each word is tappable → shows specific improvement tip
9. Overall score displayed with encouragement message
10. "Try Again" button to re-record

### Tasks
- [x] Create `PracticeScreen` composable with sections:
  - Mantra display (Devanagari + transliteration, toggleable)
  - Benchmark audio player with play/pause button + progress bar
  - Mic button (animated pulse when recording)
  - Results panel (word grid with color coding)
  - Feedback detail bottom sheet (per-word tips)
  - Overall score with animated counter
- [x] Create `PracticeModeViewModel`:
  - States: Idle → Listening → Processing → ShowingResults
  - Manages `SpeechRecognitionService` lifecycle
  - Calls `EvaluateRecitationUseCase`
  - Exposes `MantraEvaluation` as UI state
- [x] Animated mic button:
  - Idle: static mic icon
  - Listening: pulsing rings animation + waveform visualization
  - Processing: spinning indicator
- [x] Word feedback grid:
  - Each word rendered in a rounded chip
  - Color-coded by score
  - Tap to expand feedback
- [x] Benchmark audio integration:
  - Play/pause toggle
  - Word-by-word highlight during playback

### Deliverables
- `presentation/screens/practice/PracticeScreen.kt`
- `presentation/screens/practice/PracticeModeViewModel.kt`
- `presentation/screens/practice/components/MantraDisplay.kt`
- `presentation/screens/practice/components/MicButton.kt`
- `presentation/screens/practice/components/WordFeedbackGrid.kt`
- `presentation/screens/practice/components/FeedbackBottomSheet.kt`
- `presentation/screens/practice/components/AudioPlayerBar.kt`

### Acceptance Criteria
- Full flow: listen → record → see results
- Per-word color coding matches evaluation scores
- Benchmark audio plays without issues
- Smooth animations for mic and score reveal
- Practice sessions saved to DB (but don't count toward target)

---

## 3.2 Normal Recitation Mode Screen

### User Flow
1. User lands on Recitation screen
2. Sees active target: "108 recitations — 23/108 completed — ₹500 locked"
3. Taps "🎤 Begin Recitation" — continuous listening starts
4. User recites mantra continuously
5. App detects end of one recitation (silence gap or pattern match)
6. If correct (score ≥ 0.85): counter increments with animation, brief green flash
7. If incorrect: brief red flash, shows quick tip, counter doesn't increment
8. Listening resumes automatically for next recitation
9. User can pause/stop anytime
10. On target completion → celebration animation + money unlocked message

### Tasks
- [x] Create `RecitationScreen` composable:
  - Target progress header (circular progress + count)
  - Money locked display
  - Large central mic button
  - Live counter with animation
  - Recent recitation feed (last 5: ✅/❌ with timestamp)
  - Pause/Stop controls
- [x] Create `RecitationViewModel`:
  - States: Idle → Active → Paused → GoalReached
  - Continuous listening mode: auto-restart STT after each result
  - Calls `EvaluateRecitationUseCase` for each detected recitation
  - Calls `IncrementTargetUseCase` on correct recitation
  - Handles edge cases: no active target, target already completed
- [x] **Recitation boundary detection**:
  - Detect end of mantra via silence gap (>2 seconds) OR
  - Detect word "prachodayat" (last word) as boundary signal
  - Buffer partial results until boundary detected
- [x] **Continuous mode state machine**:
  ```
  IDLE → (start) → LISTENING → (speech detected) → ACCUMULATING
       → (boundary detected) → EVALUATING → (result) → FEEDBACK
       → (auto-resume after 1.5s) → LISTENING → ...
       → (pause pressed) → PAUSED → (resume) → LISTENING
       → (stop pressed) → IDLE
  ```
- [x] Celebration animation on goal reached:
  - Confetti particles
  - "🎉 Goal Reached!" message
  - "₹500 unlocked!" with money animation
  - Option to set new target

### Deliverables
- `presentation/screens/recitation/RecitationScreen.kt`
- `presentation/screens/recitation/RecitationViewModel.kt`
- `presentation/screens/recitation/components/TargetProgressHeader.kt`
- `presentation/screens/recitation/components/LiveCounter.kt`
- `presentation/screens/recitation/components/RecitationFeed.kt`
- `presentation/screens/recitation/components/CelebrationOverlay.kt`
- `domain/usecase/DetectRecitationBoundaryUseCase.kt`

### Acceptance Criteria
- Continuous recitation counting works hands-free
- Counter only increments on correct recitations
- Boundary detection correctly segments continuous speech
- Pause/resume works without losing count
- Goal completion triggers celebration
- All sessions saved to DB with correct scores

---

## 3.3 Real-Time Feedback Overlay (Both Modes)

### Tasks
- [x] Create `RecitationFeedbackOverlay` — shared component:
  - Brief inline feedback after each recitation
  - Shows: ✅ Correct / ❌ Try again
  - Quick tip for worst-scoring word
  - Auto-dismisses after 2 seconds in normal mode
  - Persists until dismissed in practice mode
- [x] Create `ImprovementSuggestionEngine`:
  - Analyzes patterns across multiple sessions
  - Identifies consistently mispronounced words
  - Generates targeted practice suggestions:
    - "You often struggle with 'varenyam' — try breaking it down: va-re-nyam"
    - "The word 'dhimahi' needs clearer 'dh' sound"

### Deliverables
- `presentation/components/RecitationFeedbackOverlay.kt`
- `domain/evaluation/ImprovementSuggestionEngine.kt`

### Acceptance Criteria
- Feedback shows immediately after evaluation
- Suggestions are contextual and helpful
- Auto-dismiss timing works in normal mode

---

## Exit Criteria

| Criterion | Metric |
|-----------|--------|
| Practice mode complete | Listen → Record → Per-word feedback |
| Normal mode complete | Continuous counting with auto-restart |
| Boundary detection | Correctly segments 90%+ of recitations |
| Animations smooth | No jank on mid-range device |
| Goal celebration | Triggers correctly on target reached |
| Feedback useful | Per-word tips generated for low-scoring words |
