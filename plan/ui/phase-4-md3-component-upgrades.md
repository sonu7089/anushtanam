# Phase 4 — MD3 Component Upgrades: Use Real Material Composables

> **Goal**: Replace custom-drawn UI elements with official MD3 composables where available, improving consistency, accessibility, and dark/dynamic theme compatibility.
>
> **Prerequisite**: Phases 1–3 complete.

---

## Current State

Several UI patterns are implemented as hand-rolled composables when official MD3 composables exist that would be more consistent, accessible, and theme-aware.

| Current Custom Component | Recommended MD3 Replacement | Priority |
|--------------------------|---------------------------|----------|
| `MantraButton` (gradient Box) | `Button` / `FilledTonalButton` / `OutlinedButton` from `androidx.compose.material3` | 🔴 High |
| `MantraCard` (shadow + gradient border Card) | `Card` / `OutlinedCard` / `ElevatedCard` | 🔴 High |
| Custom dialog in `SetTargetDialog` | `AlertDialog` from Material3 | 🟡 Medium |
| Custom settings rows | `ListItem` composable | 🟡 Medium |
| Custom role badges (Login) | `SuggestionChip` or `AssistChip` | 🟠 Low |
| Custom progress display | `LinearProgressIndicator` / `CircularProgressIndicator` (partially used, but `ProgressRing` is custom Canvas) | 🟠 Low |
| Hardcoded strings for navigation labels | String resource IDs on `Screen` sealed class | 🟡 Medium |

---

## Tasks

### 4.1 — Replace Custom Buttons with MD3 Button Hierarchy

The app uses `MantraButton` everywhere — a single-emphasis gradient button. MD3 provides 5 levels of emphasis:

| Context | Current | MD3 Replacement |
|---------|---------|-----------------|
| Primary CTA ("Start Recitation") | `MantraButton` | `Button` (filled) |
| Secondary CTA ("Practice Mode") | `MantraButton` | `FilledTonalButton` |
| Destructive / Stop | `MantraButton` | `OutlinedButton` with error color |
| Dialog confirm | `MantraButton` | `FilledTonalButton` |
| Dialog cancel | `MantraButton` | `TextButton` |

**Implementation**:
```kotlin
// Primary CTA
Button(
    onClick = onNavigateToRecitation,
    shape = MaterialTheme.shapes.extraLarge,
) {
    Text(stringResource(R.string.home_start_recitation))
}

// Secondary CTA
FilledTonalButton(
    onClick = onNavigateToPractice,
    shape = MaterialTheme.shapes.extraLarge,
) {
    Text(stringResource(R.string.home_practice_mode))
}

// Destructive / Stop
OutlinedButton(
    onClick = { viewModel.stopRecitation() },
    shape = MaterialTheme.shapes.extraLarge,
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = MaterialTheme.colorScheme.error
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
) {
    Text(stringResource(R.string.recitation_stop))
}
```

### 4.2 — Replace Custom Cards with MD3 Card Variants

Map current usage to the correct MD3 card variant:

| Usage | Variant | Why |
|-------|---------|-----|
| Today's summary (HomeScreen) | `ElevatedCard` | Key info, needs visual prominence |
| Active target (HomeScreen) | `OutlinedCard` (clickable) | Interactive, navigates to detail |
| Profile grid items (Login) | `OutlinedCard` (clickable) | Selectable items |
| Savings card | `ElevatedCard` | Financial info, highest prominence |
| Child progress cards | `Card` (filled) | Background info grouped under a section |

```kotlin
// Elevated card — uses tonal elevation, NOT .shadow()
ElevatedCard(
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
) {
    Column(Modifier.padding(16.dp)) {
        // content
    }
}

// Outlined clickable card
OutlinedCard(
    onClick = { /* navigate */ },
    modifier = modifier,
    shape = MaterialTheme.shapes.medium,
) {
    Column(Modifier.padding(16.dp)) {
        // content
    }
}
```

### 4.3 — Use MD3 `AlertDialog` for Target Setting

Replace custom dialog layout with official `AlertDialog`:

```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.set_target_title)) },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                state = countState,
                label = { Text(stringResource(R.string.target_count_label)) },
                // …
            )
            OutlinedTextField(
                state = moneyState,
                label = { Text(stringResource(R.string.target_money_label)) },
                // …
            )
        }
    },
    confirmButton = {
        FilledTonalButton(onClick = onConfirm) {
            Text(stringResource(R.string.set_target_confirm))
        }
    },
    dismissButton = {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.cancel))
        }
    },
    shape = MaterialTheme.shapes.extraLarge,
)
```

### 4.4 — Use MD3 `ListItem` for Settings Rows

Replace custom `Row`-based settings items with `ListItem`:

```kotlin
ListItem(
    headlineContent = { Text(stringResource(R.string.settings_dark_mode)) },
    supportingContent = { Text(stringResource(R.string.settings_dark_mode_desc)) },
    leadingContent = {
        Icon(Icons.Outlined.DarkMode, contentDescription = null)
    },
    trailingContent = {
        Switch(
            checked = isDarkMode,
            onCheckedChange = { viewModel.toggleDarkMode(it) }
        )
    }
)
```

Benefits:
- Consistent 56dp height, 16dp padding
- Proper leading/trailing content positioning
- Built-in tonal surface colors
- Accessible touch targets

### 4.5 — Use Chips for Profile Type Badges

Replace the custom `Box` + `Text` badge on login profile cards:

```kotlin
SuggestionChip(
    onClick = { },
    label = { Text(stringResource(badgeTextRes)) },
    shape = MaterialTheme.shapes.small,
    colors = SuggestionChipDefaults.suggestionChipColors(
        containerColor = badgeColor.copy(alpha = 0.15f),
        labelColor = badgeColor,
    ),
    border = null,
    enabled = false, // non-interactive badge
)
```

Or use `AssistChip` if the badge should carry an icon.

### 4.6 — Evaluate Custom `ProgressRing` vs `CircularProgressIndicator`

The custom `ProgressRing` is a Canvas-drawn determinate circle. MD3's `CircularProgressIndicator` supports determinate mode, but doesn't support placing content in the center.

**Decision**: Keep `ProgressRing` as a custom composable but ensure it:
- Uses `MaterialTheme.colorScheme.primary` for active arc ✅ (already correct)
- Uses `MaterialTheme.colorScheme.surfaceContainerHighest` for track (fix needed)
- Uses `MaterialTheme.shapes` for any enclosing containers

### 4.7 — Add Icon Differentiation for Active/Inactive Navigation

Expand `Screen` sealed class or the nav item data structure:

```kotlin
data class NavItem(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
)

val navItems = listOf(
    NavItem(Screen.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    NavItem(Screen.Practice, R.string.nav_practice, Icons.Filled.Edit, Icons.Outlined.Edit),
    NavItem(Screen.Recitation, R.string.nav_recite, Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
    NavItem(Screen.Progress, R.string.nav_progress, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    NavItem(Screen.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)
```

---

## Files Changed

| File | Action |
|------|--------|
| `presentation/components/MantraComponents.kt` | **Modify** — replace `MantraButton`/`MantraCard` internals with MD3 composables |
| `presentation/screens/home/HomeScreen.kt` | **Modify** — use `ElevatedCard`/`OutlinedCard`, button hierarchy |
| `presentation/screens/login/LoginScreen.kt` | **Modify** — chips for badges |
| `presentation/screens/targets/components/SetTargetDialog.kt` | **Rewrite** — `AlertDialog` |
| `presentation/screens/settings/SettingsScreen.kt` | **Modify** — `ListItem` composable |
| `presentation/screens/recitation/RecitationScreen.kt` | **Modify** — button variants |
| `presentation/navigation/MantraNavGraph.kt` | **Modify** — icon differentiation, `NavItem` data class |
| `presentation/navigation/Screen.kt` | **Modify** — add `@StringRes labelRes` |
| `res/values/strings.xml` | **Modify** — add nav label strings |

## Acceptance Criteria

- No custom `Box` + `clickable` used where an MD3 `Button` variant would work.
- No custom `Card` wrapper using `.shadow()` — all cards use `Card`/`OutlinedCard`/`ElevatedCard`.
- Dialog uses `AlertDialog`.
- Settings uses `ListItem`.
- Navigation icons differentiate active/inactive.
- All button emphasis levels are visually distinct (not all the same gradient).

## Estimated Effort

~3–4 hours.
