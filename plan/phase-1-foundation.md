# Phase 1: Foundation — Project Setup, DB, Auth & Navigation

**Duration**: ~3 days  
**Goal**: Bootable app with login flow, profile management, Room database, and navigation skeleton.

---

## 1.1 Project Initialization

- [ ] Create Android project with Kotlin DSL (`build.gradle.kts`)
- [ ] Configure version catalog (`libs.versions.toml`) with pinned deps
- [ ] Set up Hilt for DI
- [ ] Set up Compose with Material 3 and custom theme

**Exit**: App builds and launches to a blank Compose screen.

---

## 1.2 Design System & Theme

- [ ] Color palette — Primary: Deep Saffron (#FF6F00), Secondary: Sacred Gold (#FFD54F), Background: Warm Ivory (#FFF8E1) / Dark: Deep Charcoal (#1A1A2E), Accent: Spiritual Purple (#7C4DFF)
- [ ] Typography — Poppins (English) + Noto Sans Devanagari (Hindi)
- [ ] Reusable composables: `MantraButton`, `MantraCard`, `MantraTextField`, `ProfileAvatar`, `ProgressRing`
- [ ] Light + Dark theme support

**Deliverables**: `theme/Color.kt`, `theme/Type.kt`, `theme/Theme.kt`, `components/*.kt`

---

## 1.3 Room Database

### Entities

| Entity | Key Fields |
|--------|-----------|
| `UserProfileEntity` | id, name, profileType (PARENT/CHILD/INDIVIDUAL), parentId?, avatarIndex |
| `TargetEntity` | id, userId, setByUserId, targetRecitationCount, currentRecitationCount, moneyAmount, isCompleted |
| `RecitationSessionEntity` | id, userId, mode (PRACTICE/NORMAL), overallScore, wordScoresJson, transcription, isCorrect, durationMs |

### Demo Seed Data
- "Arjun" (Individual), "Meera" (Parent), "Aarav" (Child of Meera), "Priya" (Individual)

- [ ] Create DAOs, TypeConverters, Database class
- [ ] Pre-populate demo data via `RoomDatabase.Callback`

---

## 1.4 Repository Layer

- [ ] `UserRepository` — getAll, getById, getChildrenOf(parentId)
- [ ] `TargetRepository` — create, update, getActiveForUser, complete
- [ ] `RecitationRepository` — save session, getSessionsForUser, getStats
- [ ] Wire through Hilt modules

---

## 1.5 Login / Profile Selection

- [ ] `LoginScreen` — grid of demo profiles with avatar, name, type badge
- [ ] Tap to select → store in `SessionManager` singleton → navigate to Home
- [ ] No real auth — just profile selection for demo

---

## 1.6 Navigation

- [ ] Routes: Login, Home, Practice, Recitation, Progress, Targets, ChildProgress, Settings
- [ ] Bottom nav bar: Home, Practice, Recitation, Progress, Settings
- [ ] Role-based nav: Parent sees "Children" tab
- [ ] Back navigation works correctly

---

## 1.7 Home Screen (Shell)

- [ ] Greeting card with user name + avatar
- [ ] Today's recitation summary (placeholder)
- [ ] Active target progress card (placeholder)
- [ ] Quick action buttons: "Start Recitation", "Practice Mode"

---

## Exit Criteria

| Criterion | Metric |
|-----------|--------|
| App builds and runs | No crashes on launch |
| Demo login works | Select any of 4 profiles |
| Navigation functional | All screens reachable |
| Database operational | CRUD on all 3 entities |
| Theme consistent | Saffron/gold palette, light+dark |
| Tests passing | ≥10 unit + ≥5 integration tests |
