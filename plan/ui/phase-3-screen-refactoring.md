# Phase 3 — Screen-Level Refactoring: Proper Scaffold, App Bars & Navigation

> **Goal**: Replace ad-hoc screen layouts with MD3 `Scaffold` + `TopAppBar` patterns, fix navigation bar theming, and eliminate all remaining hardcoded colors/shapes in screen-level code.
>
> **Prerequisite**: Phases 1 & 2 complete.

---

## Current State

### Navigation (`MantraNavGraph.kt`)

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp)` — MD3 nav bar should use `surfaceContainer` at Level 2, not explicit `surface` + 8dp tonal | Nav bar default container = `surface-container` | 🟡 Medium |
| Bottom bar label strings hardcoded in Kotlin (`"Home"`, `"Practice"`, etc.) | AGENTS.md rule: no hardcoded strings in UI — must use `strings.xml` | 🟡 Medium |
| `Icons.Default.*` (filled icons) used for both active and inactive states | MD3 spec: active = filled icon, inactive = outlined icon | 🟡 Medium |
| Transition animations use `tween(350, easing = EaseInOutCubic)` — MD3 specifies `Emphasized` easing at 500ms for shared-axis | Motion spec mismatch | 🟠 Low |

### Splash Screen (`SplashActivity.kt`)

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| 3 hardcoded `Color(0x…)` in gradient | Use scheme colors | 🔴 Critical |
| `tween(durationMillis = 800)` — custom motion, not MD3 spec | MD3 enter = emphasized decelerate 400ms | 🟡 Medium |
| No edge-to-edge — status bar color set manually via `window.statusBarColor` (deprecated approach) | Use `enableEdgeToEdge()` + system bar insets | 🟡 Medium |

### Login Screen (`LoginScreen.kt`)

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `fontSize = 24.sp` hardcoded instead of `MaterialTheme.typography.headlineSmall` | Use type scale roles | 🟡 Medium |
| `RoundedCornerShape(12.dp)` for badge | `MaterialTheme.shapes.medium` | 🟡 Medium |
| No `TopAppBar` — screen has custom header layout | Screens should use `Scaffold` + `TopAppBar` where appropriate |🟠 Low (login is a special screen) |

### Home Screen (`HomeScreen.kt`)

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| Uses `Surface(color = MaterialTheme.colorScheme.background)` as root | MD3: use `Scaffold` which handles `surface` background automatically | 🟡 Medium |
| `"Have a blessed day"` — hardcoded string | Use `strings.xml` | 🟡 Medium |
| `"Quick Actions"` — hardcoded string | Use `strings.xml` | 🟡 Medium |
| `"• 🔥 ${state.streakDays} Day Streak"` — hardcoded | Use `strings.xml` with placeholder | 🟡 Medium |
| `fontWeight = FontWeight.Bold` overrides on many `Text` composables | Let the type scale handle weight; use `MaterialTheme.typography.titleMedium` without overrides unless deliberately emphasized | 🟠 Low |

### Practice Screen

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `Color(0xFFC62828)` — hardcoded error red | Use `MaterialTheme.colorScheme.error` or `ExtendedColors` | 🔴 Critical |
| `Color(0xFF2E7D32)` / `Color(0xFFE65100)` — hardcoded success/warning | Use `ExtendedColors.success` / `.warning` | 🔴 Critical |
| Multiple `RoundedCornerShape(20.dp)`, `(4.dp)` in sub-components | Use `MaterialTheme.shapes.*` | 🟡 Medium |

### Recitation Screen Sub-Components

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `CelebrationOverlay.kt` — `RoundedCornerShape(24.dp)`, `(12.dp)` | Shapes | 🟡 Medium |
| `RecitationFeed.kt` — `Color(0xFFE8F5E9)`, `Color(0xFF2E7D32)`, `Color(0xFFFFEBEE)`, `Color(0xFFC62828)` | Extended colors | 🔴 Critical |

### Progress Screen

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `WordMasteryGrid.kt` — 3 hardcoded status colors for mastered/learning/focus | Extended colors | 🔴 Critical |
| `SessionHistoryList.kt` — `RoundedCornerShape(12.dp)`, `(4.dp)` | Shapes | 🟡 Medium |
| `ProgressScreen.kt` — `RoundedCornerShape(16.dp)` | Shapes | 🟡 Medium |

### Targets Screen

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `SetTargetDialog.kt` — `RoundedCornerShape(20.dp)`, `(16.dp)` | Use `MaterialTheme.shapes.extraLarge` for dialog | 🟡 Medium |
| `CompletedTargetItem.kt` — `Color(0xFF4CAF50)` for success icon and text | Extended colors | 🔴 Critical |

### Settings Screen

| Problem | MD3 Rule | Severity |
|---------|----------|----------|
| `RoundedCornerShape(6.dp)`, `(8.dp)` — multiple instances | Shapes | 🟡 Medium |
| Uses a custom layout for settings rows — could benefit from MD3 `ListItem` composable | Component compliance | 🟠 Low |

---

## Tasks

### 3.1 — Fix Navigation Bar Theming & Icons

**File**: `MantraNavGraph.kt`

```kotlin
NavigationBar(
    // Let MD3 defaults handle container color and elevation.
    // Default: surfaceContainer at Level 2 tonal elevation.
) {
    items.forEach { (screen, info) ->
        val (label, activeIcon, inactiveIcon) = info
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = if (isSelected) activeIcon else inactiveIcon,
                    contentDescription = label
                )
            },
            label = { Text(text = stringResource(screen.labelResId)) },
            selected = currentRoute == screen.route,
            // …
        )
    }
}
```

Changes:
- Remove explicit `containerColor` and `tonalElevation` — let MD3 defaults apply.
- Add `Screen.labelResId` string resources; replace hardcoded labels.
- Use `Icons.Outlined.*` for inactive state, `Icons.Filled.*` for active.

### 3.2 — Adopt MD3 Motion Spec for Transitions

Replace `tween(350, easing = EaseInOutCubic)` with MD3 emphasized easing:

```kotlin
// MD3 Emphasized easing (begin and end on screen)
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Apply in NavHost
enterTransition = {
    slideIntoContainer(
        direction,
        animationSpec = tween(500, easing = EmphasizedEasing)
    ) + fadeIn(animationSpec = tween(500))
}
```

Store easing constants in `presentation/theme/Motion.kt`.

### 3.3 — Wrap Screens in Scaffold + TopAppBar

For screens that currently use raw `Surface` or `Box` as root:

```kotlin
// Example: HomeScreen
Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.home_greeting, state.user.name),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            actions = {
                ProfileAvatar(
                    avatarIndex = state.user.avatarIndex,
                    name = state.user.name,
                    size = 36.dp
                )
            }
        )
    }
) { innerPadding ->
    Column(
        modifier = Modifier
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        // Content
    }
}
```

Screens to wrap:
- **HomeScreen** — `TopAppBar` (small) with greeting + avatar
- **PracticeScreen** — `TopAppBar` (center-aligned) with "Practice Mode" title
- **RecitationScreen** — `TopAppBar` (center-aligned) with "Recitation" title
- **ProgressScreen** — Already has its own header; wrap in `Scaffold`
- **SettingsScreen** — `TopAppBar` with "Settings" title
- **LoginScreen** — No `Scaffold` needed (full-bleed splash-style)

### 3.4 — Eliminate All Remaining Hardcoded Colors in Screens

Target files:
- `practice/PracticeScreen.kt` — 5 instances
- `practice/components/FeedbackBottomSheet.kt` — 3 instances
- `practice/components/WordFeedbackGrid.kt` — hardcoded colors
- `practice/components/MantraDisplay.kt` — `RoundedCornerShape` instances
- `recitation/components/CelebrationOverlay.kt` — shapes
- `recitation/components/RecitationFeed.kt` — 4 instances
- `progress/ProgressScreen.kt` — shapes
- `progress/components/WordMasteryGrid.kt` — 3 status colors + shapes
- `progress/components/SessionHistoryList.kt` — shapes
- `targets/components/SetTargetDialog.kt` — shapes
- `targets/components/CompletedTargetItem.kt` — 2 success colors
- `settings/SettingsScreen.kt` — shapes
- `childprogress/ChildProgressScreen.kt` — shapes
- `childprogress/components/ProgressChart.kt` — shapes

### 3.5 — Enable Edge-to-Edge

**File**: `MainActivity.kt` and `SplashActivity.kt`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge() // from androidx.activity
    super.onCreate(savedInstanceState)
    // …
}
```

Remove deprecated `window.statusBarColor = …` and `WindowCompat.getInsetsController(…).isAppearanceLightStatusBars = …` from `Theme.kt`.

### 3.6 — Refactor Splash Screen

Replace hardcoded gradient colors with scheme colors:

```kotlin
Box(
    modifier = modifier
        .fillMaxSize()
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer,
                )
            )
        ),
    // …
)
```

Use MD3 emphasized decelerate easing for the scale animation.

---

## Files Changed

| File | Action |
|------|--------|
| `presentation/navigation/MantraNavGraph.kt` | **Modify** — nav bar theming, icons, labels, motion |
| `presentation/theme/Motion.kt` | **Create** — MD3 easing constants |
| `presentation/SplashActivity.kt` | **Modify** — scheme colors, edge-to-edge |
| `presentation/MainActivity.kt` | **Modify** — enable edge-to-edge |
| `presentation/theme/Theme.kt` | **Modify** — remove deprecated status bar code |
| `presentation/screens/home/HomeScreen.kt` | **Modify** — Scaffold + TopAppBar, string resources |
| `presentation/screens/practice/PracticeScreen.kt` | **Modify** — Scaffold, hardcoded colors |
| `presentation/screens/practice/components/*.kt` | **Modify** — colors, shapes |
| `presentation/screens/recitation/RecitationScreen.kt` | **Modify** — Scaffold |
| `presentation/screens/recitation/components/*.kt` | **Modify** — colors, shapes |
| `presentation/screens/progress/ProgressScreen.kt` | **Modify** — Scaffold, shapes |
| `presentation/screens/progress/components/*.kt` | **Modify** — colors, shapes |
| `presentation/screens/targets/components/*.kt` | **Modify** — colors, shapes |
| `presentation/screens/settings/SettingsScreen.kt` | **Modify** — Scaffold, shapes |
| `presentation/screens/childprogress/*.kt` | **Modify** — Scaffold, shapes |
| `res/values/strings.xml` | **Modify** — add missing string resources |

## Acceptance Criteria

- `grep -rn 'Color(0x' --include='*.kt' presentation/screens/` returns **0 results**.
- `grep -rn 'RoundedCornerShape' --include='*.kt' presentation/screens/` returns **0 results** (all use `MaterialTheme.shapes.*`).
- Every screen (except Login) is wrapped in `Scaffold` with a `TopAppBar`.
- Navigation bar uses default MD3 container color, outlined icons for inactive state.
- All user-facing strings come from `strings.xml`.
- App renders correctly edge-to-edge with proper inset handling.
- No `window.statusBarColor` anywhere in the codebase.

## Estimated Effort

~5–6 hours (largest phase — touches every screen file).
