# Phase 5: Progress Analytics, Polish & Demo Readiness

**Duration**: ~3 days  
**Goal**: Progress/analytics screen, UI polish, edge-case hardening, and demo-ready packaging.

**Depends on**: Phase 4 complete.

---

## 5.1 Progress & Analytics Screen

### Tasks
- [ ] Create `ProgressScreen` with tabs:
  - **Overview**: Total recitations, accuracy rate, streak, best session
  - **Daily Chart**: Bar chart of recitations per day (last 7/30 days)
  - **Accuracy Trend**: Line chart of average scores over time
  - **Word Mastery**: Heat map of per-word average scores across all sessions
  - **Session History**: Scrollable list of all sessions with score, mode, timestamp
- [ ] Charts rendered using **Compose Canvas** (no external library):
  - Animated bar chart with gradient fills
  - Smooth line chart with dot markers
  - Color-coded word mastery grid
- [ ] Create analytics use cases:
  - `GetRecitationStatsUseCase` — aggregates overall stats
  - `GetDailyRecitationCountsUseCase` — groups sessions by day
  - `GetAccuracyTrendUseCase` — average score per day
  - `GetWordMasteryUseCase` — per-word average across all sessions

### Deliverables
- `presentation/screens/progress/ProgressScreen.kt`
- `presentation/screens/progress/ProgressViewModel.kt`
- `presentation/screens/progress/components/DailyBarChart.kt`
- `presentation/screens/progress/components/AccuracyLineChart.kt`
- `presentation/screens/progress/components/WordMasteryGrid.kt`
- `presentation/screens/progress/components/SessionHistoryList.kt`
- `domain/usecase/GetRecitationStatsUseCase.kt`
- `domain/usecase/GetDailyRecitationCountsUseCase.kt`
- `domain/usecase/GetAccuracyTrendUseCase.kt`
- `domain/usecase/GetWordMasteryUseCase.kt`

### Acceptance Criteria
- Charts render correctly with real data
- Animations smooth on chart load
- Word mastery grid accurately reflects per-word performance
- Empty state handled (new user with no sessions)

---

## 5.2 Settings Screen

### Tasks
- [ ] Create `SettingsScreen`:
  - Profile info (name, type, avatar)
  - Theme toggle (Light/Dark/System)
  - Language preference for mantra display (Devanagari / Transliteration / Both)
  - Recitation sensitivity slider (adjust correct threshold: 0.75–0.95)
  - About section (app version, credits)
  - "Reset Demo Data" button (re-seeds database)
  - Switch profile (navigate back to login)
- [ ] Create `SettingsViewModel` with `UserPreferencesDataStore`
- [ ] Persist preferences using Jetpack DataStore (Preferences)

### Deliverables
- `presentation/screens/settings/SettingsScreen.kt`
- `presentation/screens/settings/SettingsViewModel.kt`
- `data/preferences/UserPreferencesDataStore.kt`

### Acceptance Criteria
- All settings persist across app restarts
- Theme change applies immediately
- Reset demo data works without crash
- Sensitivity slider affects evaluation threshold in real-time

---

## 5.3 UI Polish & Animations

### Tasks
- [ ] Add screen transition animations (slide, fade)
- [ ] Add haptic feedback on:
  - Correct recitation (subtle)
  - Goal reached (strong)
  - Button taps (light)
- [ ] Add sound effects:
  - Soft bell on correct recitation
  - Achievement sound on goal completion
- [ ] Loading skeletons for all data screens
- [ ] Empty state illustrations:
  - No sessions yet → "Begin your journey" with lotus illustration
  - No target set → "Set your first goal" prompt
  - No children → "Add a child profile" for parent
- [ ] Splash screen with app logo and warm gradient
- [ ] Review all screens for visual consistency
- [ ] Ensure all text uses `strings.xml` (i18n ready)

### Deliverables
- Updated composables across all screens
- `res/raw/bell_correct.mp3`, `res/raw/achievement.mp3`
- `presentation/components/LoadingSkeleton.kt`
- `presentation/components/EmptyState.kt`
- Splash screen activity/theme

### Acceptance Criteria
- Animations feel native and smooth
- No visual glitches or layout jumps
- Consistent spacing, colors, typography everywhere
- Haptic feedback works on supported devices

---

## 5.4 Edge Cases & Error Handling

### Tasks
- [ ] Handle STT unavailability gracefully (show message, disable mic)
- [ ] Handle mic permission denied (explain why needed, deep link to settings)
- [ ] Handle no active target in recitation mode (prompt to set one)
- [ ] Handle database errors (show retry option)
- [ ] Handle very fast/very slow recitations (timeout + minimum duration)
- [ ] Handle background/foreground transitions during recitation
- [ ] Handle device rotation during active session
- [ ] Test with accessibility services (TalkBack)

### Deliverables
- Error handling improvements across all ViewModels
- `presentation/components/ErrorScreen.kt`
- `presentation/components/PermissionRationale.kt`

### Acceptance Criteria
- No crashes on any error path
- User always sees actionable feedback on errors
- App recovers gracefully from background/foreground

---

## 5.5 Demo Data Polish

### Tasks
- [ ] Pre-populate richer demo data:
  - Each profile has 10-20 pre-existing recitation sessions (varied scores)
  - Active targets with partial progress
  - Some completed targets in history
  - Savings balances reflecting completed targets
- [ ] Ensure demo data tells a story:
  - Arjun (Individual): Consistent practitioner, 85% accuracy, 45/108 target
  - Meera (Parent): Occasional practitioner, has set target for child
  - Aarav (Child): Improving beginner, 65% accuracy, 12/108 target
  - Priya (Individual): Advanced, high accuracy, multiple completed targets

### Deliverables
- Updated `DemoDataSeeder.kt` with rich, narrative demo data

### Acceptance Criteria
- Charts and progress screens look populated and meaningful
- Demo profiles tell distinct stories
- App feels "lived in" on first launch

---

## 5.6 Final Testing & Demo Readiness

### Tasks
- [ ] Full regression test across all screens and flows
- [ ] Test on at least 2 device sizes (phone + tablet)
- [ ] Test offline mode end-to-end
- [ ] Performance profiling (no dropped frames, <2s cold start)
- [ ] Generate signed demo APK
- [ ] Create demo walkthrough script (what to show, in what order)

### Deliverables
- Signed APK in `build/` directory
- `docs/DEMO_SCRIPT.md` — step-by-step demo walkthrough
- Test results summary

### Acceptance Criteria
- Zero crashes during demo walkthrough
- All features functional end-to-end
- APK installs and runs on clean device
- Demo script covers all major features in 10 minutes

---

## Exit Criteria (Project Complete)

| Criterion | Metric |
|-----------|--------|
| All 5 phases complete | Every task checked off |
| Zero known crashes | Tested across demo walkthrough |
| Feature complete | Login, Practice, Recitation, Targets, Progress, Parent-Child, Settings |
| Demo ready | APK installable, demo data rich, walkthrough script written |
| Code quality | AGENTS.md conventions followed, tests passing |
| Offline capable | Full functionality without network |
