# AGENTS.md — Gayatri Mantra Recitation App

## Project Overview

**App Name**: Mantra Siddhi (मंत्र सिद्धि)  
**Platform**: Android (Kotlin, Jetpack Compose)  
**Architecture**: MVVM + Clean Architecture  
**Database**: Room (on-device SQLite)  
**Speech Recognition**: Android on-device SpeechRecognizer API  
**Min SDK**: 26 (Android 8.0)  
**Target SDK**: 34 (Android 14)

---

## Coding Standards & Conventions

### 1. Language & Framework
- **Kotlin** as the sole language. No Java.
- **Jetpack Compose** for all UI. No XML layouts.
- **Coroutines + Flow** for all async operations. No RxJava, no callbacks.
- **Hilt** for dependency injection.

### 2. Architecture Rules
```
app/
├── data/               # Data layer
│   ├── local/          # Room DB, DAOs, entities
│   ├── repository/     # Repository implementations
│   └── model/          # Data transfer objects
├── domain/             # Domain layer (pure Kotlin, no Android deps)
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   ├── usecase/        # Use cases (single-responsibility)
│   └── evaluation/     # Mantra evaluation engine
├── presentation/       # Presentation layer
│   ├── navigation/     # Nav graph, routes
│   ├── theme/          # Material 3 theme, colors, typography
│   ├── components/     # Reusable composables
│   └── screens/        # Feature screens (1 package per screen)
│       ├── login/
│       ├── home/
│       ├── practice/
│       ├── recitation/
│       ├── progress/
│       ├── targets/
│       └── settings/
└── di/                 # Hilt modules
```

### 3. Naming Conventions
| Element | Convention | Example |
|---------|-----------|---------|
| Package | lowercase, dot-separated | `com.mantrasiddhi.presentation.screens.practice` |
| Class/Object | PascalCase | `RecitationViewModel` |
| Function | camelCase | `evaluateRecitation()` |
| Composable | PascalCase (noun) | `MantraFeedbackCard()` |
| Constant | SCREAMING_SNAKE | `MAX_RECITATION_COUNT` |
| DB Entity | PascalCase + `Entity` suffix | `UserProfileEntity` |
| DAO | PascalCase + `Dao` suffix | `RecitationDao` |
| Repository Interface | PascalCase + `Repository` | `RecitationRepository` |
| Repository Impl | PascalCase + `RepositoryImpl` | `RecitationRepositoryImpl` |
| UseCase | Verb + Noun + `UseCase` | `CountRecitationUseCase` |
| ViewModel | Screen + `ViewModel` | `PracticeModeViewModel` |

### 4. Code Quality Rules
- **No magic numbers.** All constants in a `Constants.kt` or domain-specific constants file.
- **No hardcoded strings in UI.** All user-facing strings in `strings.xml`.
- **All public functions must have KDoc.** Internal functions should have comments explaining *why*, not *what*.
- **Maximum function length: 30 lines.** Extract helpers aggressively.
- **Maximum class length: 300 lines.** Split into focused classes.
- **Single Responsibility Principle enforced per class and per UseCase.**
- **No business logic in ViewModels.** ViewModels orchestrate UseCases only.
- **No Android framework imports in `domain/` layer.** Keep it pure Kotlin.

### 5. State Management
- **UiState sealed interface** per screen:
  ```kotlin
  sealed interface RecitationUiState {
      data object Loading : RecitationUiState
      data class Active(val count: Int, val target: Int, ...) : RecitationUiState
      data class Error(val message: String) : RecitationUiState
  }
  ```
- **UiEvent sealed class** for one-shot events (navigation, toasts):
  ```kotlin
  sealed class RecitationEvent {
      data class ShowFeedback(val feedback: MantraFeedback) : RecitationEvent()
      data object GoalReached : RecitationEvent()
  }
  ```
- **StateFlow** for state, **SharedFlow** for events in ViewModels.

### 6. Testing Strategy
- **Unit Tests**: All UseCases, Repository logic, and the Evaluation Engine. Use JUnit 5 + MockK.
- **Integration Tests**: Room database operations. Use in-memory DB.
- **UI Tests**: Critical flows (login, recitation, goal tracking). Use Compose testing APIs.
- **Test naming**: `should_expectedBehavior_when_condition()`
  ```kotlin
  fun should_incrementCount_when_correctRecitationDetected()
  ```
- **Minimum coverage target**: 80% for `domain/`, 60% for `data/`, 40% for `presentation/`.

### 7. Git Workflow
- **Branch naming**: `phase-{N}/{feature-name}` (e.g., `phase-1/room-database-setup`)
- **Commit messages**: Conventional Commits format
  - `feat: add recitation counting logic`
  - `fix: normalize transcription text for Hindi diacritics`
  - `test: add evaluation engine unit tests`
  - `docs: update phase 2 plan with API details`
- **PR rule**: Each phase = 1 PR with sub-commits per feature.

### 8. Error Handling
- **Domain layer**: Return `Result<T>` or sealed `Outcome<T>` types. No exceptions for business logic.
- **Data layer**: Catch exceptions at repository boundary, convert to domain errors.
- **Presentation layer**: Map domain errors to user-friendly messages via `strings.xml`.

### 9. Accessibility
- All interactive elements must have `contentDescription`.
- Minimum touch target: 48dp.
- Support for TalkBack screen reader.
- Dynamic text sizing support.

### 10. Performance Guidelines
- **Speech recognition**: Use `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE = true`.
- **Database writes**: Always on `Dispatchers.IO`.
- **UI renders**: Keep composables idempotent and side-effect free.
- **Audio playback**: Use `MediaPlayer` with proper lifecycle management.

---

## Key Domain Definitions

### Gayatri Mantra Reference Text
```
ॐ भूर्भुवः स्वः
तत्सवितुर्वरेण्यं
भर्गो देवस्य धीमहि
धियो यो नः प्रचोदयात्
```

**Transliteration (for STT matching)**:
```
om bhur bhuvah svah
tat savitur varenyam
bhargo devasya dhimahi
dhiyo yo nah prachodayat
```

### Evaluation Criteria
- **Word-level matching**: Each word of the transcription is compared against the reference.
- **Normalization pipeline**: lowercase → remove diacritics → phonetic normalization → fuzzy match.
- **Scoring**: Each word gets a score (0.0–1.0). Overall score = mean of word scores.
- **Correct recitation threshold**: Overall score ≥ 0.85.
- **Per-word feedback**: Words below 0.7 score get specific improvement suggestions.

### Profile Types
| Type | Can Set Own Goal | Can Set Child Goal | Can See Child Progress | Can Recite |
|------|-----|-----|-----|-----|
| Individual | ✅ | ❌ | ❌ | ✅ |
| Parent | ✅ | ✅ | ✅ | ✅ |
| Child | ❌ | ❌ | ❌ | ✅ |

---

## File Headers
Every Kotlin file must start with:
```kotlin
/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
```

---

## Dependencies (locked versions)
```toml
# Key dependencies - pin exact versions
androidx-core = "1.13.1"
compose-bom = "2024.06.00"
room = "2.6.1"
hilt = "2.51.1"
navigation-compose = "2.7.7"
lifecycle = "2.8.3"
material3 = "1.2.1"
```

---

## Demo Data Strategy
- Pre-seeded Room database with demo profiles on first launch.
- Demo login: No authentication — select profile from a list.
- Demo money: Virtual currency, no real transactions.
- All data persists on-device only.
