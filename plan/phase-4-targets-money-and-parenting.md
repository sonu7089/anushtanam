# Phase 4: Targets, Money System & Parent-Child Features

**Duration**: ~3 days  
**Goal**: Full target/goal management with virtual money system and parent-child relationship features.

**Depends on**: Phase 3 complete.

---

## 4.1 Target Management Screen

### User Flow
- **Individual/Parent (self)**: Set a recitation goal (e.g., 108, 1008, custom) + lock a virtual money amount
- **Parent (for child)**: Select child → set goal + money for them
- View active target progress
- View completed target history

### Tasks
- [x] Create `TargetsScreen`:
  - Active target card with circular progress, count, money
  - "Set New Target" button (disabled if active target exists)
  - Completed targets history list
- [x] Create `SetTargetDialog`:
  - Preset options: 108 (Quick), 1008 (Standard), 10008 (Advanced), Custom
  - Money input field with virtual currency (₹ symbol, demo values)
  - For Parent: child selector dropdown
  - Confirmation with summary
- [x] Create `TargetsViewModel`:
  - `GetActiveTargetUseCase`
  - `CreateTargetUseCase` — validates no duplicate active target
  - `GetCompletedTargetsUseCase`
  - `GetChildrenUseCase` (for parent)

### Deliverables
- `presentation/screens/targets/TargetsScreen.kt`
- `presentation/screens/targets/TargetsViewModel.kt`
- `presentation/screens/targets/components/SetTargetDialog.kt`
- `presentation/screens/targets/components/TargetCard.kt`
- `presentation/screens/targets/components/CompletedTargetItem.kt`
- `domain/usecase/GetActiveTargetUseCase.kt`
- `domain/usecase/CreateTargetUseCase.kt`
- `domain/usecase/GetCompletedTargetsUseCase.kt`

### Acceptance Criteria
- Can set target with recitation count + money
- Only 1 active target per user at a time
- Parent can set target for child
- Completed targets show in history with completion date
- Money amount displayed clearly

---

## 4.2 Virtual Money / Savings System

### Tasks
- [x] Create `SavingsEntity` in Room:
  ```kotlin
  @Entity(tableName = "savings")
  data class SavingsEntity(
      @PrimaryKey val userId: Long,
      val totalSaved: Double = 0.0,
      val totalLocked: Double = 0.0,
      val totalUnlocked: Double = 0.0
  )
  ```
- [x] Money flow:
  1. User sets target with ₹500 → `totalLocked += 500`
  2. User completes target → `totalLocked -= 500`, `totalUnlocked += 500`, `totalSaved += 500`
  3. If target abandoned → `totalLocked -= 500` (money returned, not saved)
- [x] Create `SavingsCard` component:
  - Shows total saved, currently locked, unlocked
  - Animated coin counter on unlock
- [x] Create `SavingsRepository` and `UpdateSavingsUseCase`
- [x] Add savings card to Home screen

### Deliverables
- `data/local/entity/SavingsEntity.kt`
- `data/local/dao/SavingsDao.kt`
- `data/repository/SavingsRepositoryImpl.kt`
- `domain/usecase/UpdateSavingsUseCase.kt`
- `presentation/components/SavingsCard.kt`

### Acceptance Criteria
- Money locks when target is set
- Money unlocks when target is completed
- Savings history accurately reflects all transactions
- Visual coin animation on unlock

---

## 4.3 Parent-Child Dashboard

### Tasks
- [x] Create `ChildProgressScreen`:
  - Shows list of children (for parent profile)
  - Each child card: name, avatar, active target progress, total recitations, savings
  - Tap child → detailed view
- [x] Create `ChildDetailScreen`:
  - Full recitation history (sessions list)
  - Progress chart (daily recitation count)
  - Current target status
  - Set new target button
  - Improvement areas (words child struggles with most)
- [x] Create `GetChildProgressUseCase`:
  - Aggregates recitation sessions, target status, and savings for a child
- [x] Create `GetChildWeakWordsUseCase`:
  - Analyzes all of child's sessions
  - Returns top 3 words with lowest average scores

### Deliverables
- `presentation/screens/childprogress/ChildProgressScreen.kt`
- `presentation/screens/childprogress/ChildDetailScreen.kt`
- `presentation/screens/childprogress/ChildProgressViewModel.kt`
- `presentation/screens/childprogress/components/ChildCard.kt`
- `presentation/screens/childprogress/components/ProgressChart.kt`
- `domain/usecase/GetChildProgressUseCase.kt`
- `domain/usecase/GetChildWeakWordsUseCase.kt`

### Acceptance Criteria
- Parent can see all children's progress
- Drill-down shows detailed stats per child
- Weak words analysis is accurate
- Parent can set targets for children from this screen
- Non-parent profiles cannot access this screen

---

## 4.4 Home Screen — Complete

### Tasks
- [x] Update `HomeScreen` with real data (replace Phase 1 placeholders):
  - Greeting with time-of-day context ("Good Morning, Meera 🙏")
  - Today's stats: recitations today, streak days
  - Active target progress ring
  - Savings summary card
  - Quick actions: Start Recitation, Practice Mode
  - For Parent: children summary cards
- [x] Create `HomeViewModel` with aggregation use cases

### Deliverables
- Updated `presentation/screens/home/HomeScreen.kt`
- Updated `presentation/screens/home/HomeViewModel.kt`
- `domain/usecase/GetDashboardStatsUseCase.kt`

### Acceptance Criteria
- All data is real (from DB, not placeholder)
- Role-specific content shows correctly
- Smooth animations on data load

---

## Exit Criteria

| Criterion | Metric |
|-----------|--------|
| Target management | Create, track, complete targets |
| Money system | Lock on set, unlock on complete |
| Parent-child | Parent sees child progress + sets targets |
| Home screen | All real data, role-specific |
| Database migration | Phase 4 entities added cleanly |
