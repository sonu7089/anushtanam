# Phase 2 — Shared Components: Eradicate Hardcoded Colors & Shadows

> **Goal**: Refactor all shared components in `presentation/components/` to use `MaterialTheme.colorScheme`, `MaterialTheme.shapes`, and tonal elevation instead of raw `Color(0x…)` values, `RoundedCornerShape(N.dp)` literals, and `.shadow()` modifiers.
>
> **Prerequisite**: Phase 1 (theme foundation) is complete and merged.

---

## Current State (50+ hardcoded `Color(0x…)` across components)

### `MantraComponents.kt` — 4 issues

| Line(s) | Problem | Fix |
|---------|---------|-----|
| 62–63 | `MantraButton` disabled state uses `Color.Gray` / `Color.LightGray` — not from scheme | Use `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)` per MD3 disabled spec |
| 68 | `RoundedCornerShape(25.dp)` — should be `MaterialTheme.shapes.extraLarge` or full round | Use `CircleShape` (stadium button) or `MaterialTheme.shapes` |
| 74 | Text uses `Color.White` — should be `MaterialTheme.colorScheme.onPrimary` | Replace |
| 101 | `.shadow(4.dp, RoundedCornerShape(16.dp))` on `MantraCard` | Remove shadow; use `tonalElevation = 1.dp` on `Card` for tonal depth, or use `CardDefaults.outlinedCardColors()` for outlined variant |
| 104–111 | Gradient border on `MantraCard` with hardcoded alpha | Replace with `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)` for outlined card, or remove for filled/elevated variant |
| 110, 112 | `RoundedCornerShape(16.dp)` hardcoded | Use `MaterialTheme.shapes.large` |
| 142 | `MantraTextField` shape `RoundedCornerShape(12.dp)` | Use `MaterialTheme.shapes.medium` |
| 162–166 | `ProfileAvatar` — 4 hardcoded gradient color pairs per avatar | Define avatar gradients using scheme roles: `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `surfaceContainerHigh` — or keep hardcoded but extract to a `AvatarColors` object in Color.kt with clear comment |
| 174 | `.shadow(4.dp, CircleShape)` on avatar | Remove shadow; circular tonal surface or `border` is sufficient |
| 180 | `Color.White` for initial text | Use `MaterialTheme.colorScheme.onPrimaryContainer` (or matching `on*` for each gradient) |
| 205 | `ProgressRing` track color uses `secondary.copy(alpha = 0.2f)` | Use `MaterialTheme.colorScheme.surfaceContainerHighest` for the track instead |

### `SavingsCard.kt` — 5 issues

| Line(s) | Problem | Fix |
|---------|---------|-----|
| 89–90 | `Color(0xFFFFA000)` / `Color(0xFFFFD54F)` for border gradient | Use `MaterialTheme.colorScheme.primaryContainer` / `outlineVariant` |
| 93 | `RoundedCornerShape(16.dp)` | `MaterialTheme.shapes.large` |
| 133 | `Color(0xFFFFD54F)` / `Color(0xFFFFB300)` for coin gradient | Use `MaterialTheme.colorScheme.primary` / `primaryContainer` |
| 192 | `Color(0xFF4CAF50)` for "unlocked success" | Use `MaterialTheme.colorScheme.tertiary` or define a custom extended color role |

### `RecitationFeedbackOverlay.kt` — 3 issues

| Line(s) | Problem | Fix |
|---------|---------|-----|
| 56–57 | `Color(0xFFE8F5E9)` / `Color(0xFF2E7D32)` for success | Define semantic `SuccessContainer` / `OnSuccessContainer` in an `ExtendedColorScheme` |
| 63–64 | `Color(0xFFFFEBEE)` / `Color(0xFFC62828)` for error | Use `MaterialTheme.colorScheme.errorContainer` / `onErrorContainer` |
| 74 | `RoundedCornerShape(16.dp)` | `MaterialTheme.shapes.large` |

### `LoadingSkeleton.kt` — 4 issues

| Line(s) | Problem | Fix |
|---------|---------|-----|
| 56–58 | `Color(0xFFE0E0E0)` / `Color(0xFFF5F5F5)` shimmer colors | Use `MaterialTheme.colorScheme.surfaceContainerHigh` / `surfaceContainerLowest` |
| 97–147 | Multiple `RoundedCornerShape(4.dp)`, `(6.dp)`, `(12.dp)` | Use `MaterialTheme.shapes.extraSmall`, `.small`, `.medium` |

### `EmptyState.kt`

Audit for any hardcoded colors (component file not fully reviewed yet — verify during implementation).

---

## Tasks

### 2.1 — Create Extended Color Roles for Success/Warning

MD3 only has `error` as a semantic status color. Anushtanam needs **success** (green) and **warning** (amber) for recitation feedback. Define these as an extension:

**New file**: `presentation/theme/ExtendedColors.kt`

```kotlin
/**
 * Extended color roles not present in MaterialTheme.colorScheme.
 * MD3 supports custom colors via harmonization with the seed.
 */
data class ExtendedColorScheme(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LightExtendedColors = ExtendedColorScheme(
    success = Color(0xFF386A20),          // Generated from green seed harmonized with #FF6F00
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB7F397),
    onSuccessContainer = Color(0xFF042100),
    warning = Color(0xFF7C5800),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDEA6),
    onWarningContainer = Color(0xFF271900),
)

val DarkExtendedColors = ExtendedColorScheme(
    success = Color(0xFF9CD67E),
    onSuccess = Color(0xFF0D3900),
    successContainer = Color(0xFF205107),
    onSuccessContainer = Color(0xFFB7F397),
    warning = Color(0xFFF5BE48),
    onWarning = Color(0xFF412D00),
    warningContainer = Color(0xFF5E4200),
    onWarningContainer = Color(0xFFFFDEA6),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
```

Wire into `AnushtanamTheme`:
```kotlin
CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
    MaterialTheme(…) { content() }
}
```

Access via:
```kotlin
val extendedColors = LocalExtendedColors.current
extendedColors.successContainer
```

### 2.2 — Refactor `MantraButton`

Replace the custom gradient `Box` + `clickable` with a proper MD3 `Button`:

```kotlin
@Composable
fun MantraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = MaterialTheme.shapes.extraLarge, // stadium-like fully rounded
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
```

This eliminates the gradient. If the brand gradient is desired, keep the `Box` approach but use scheme colors for the gradient endpoints.

### 2.3 — Refactor `MantraCard` to MD3 Card Variants

Replace the shadow-based card with a proper MD3 card variant:

```kotlin
@Composable
fun MantraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        OutlinedCard(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}
```

### 2.4 — Refactor `ProfileAvatar`

- Remove `.shadow(4.dp, CircleShape)` — use `border` if visual separation is needed.
- Define avatar color palettes from scheme roles or a dedicated `AvatarColors` object.
- Replace `Color.White` with matched `on*` role.

### 2.5 — Refactor `ProgressRing`

- Track color → `MaterialTheme.colorScheme.surfaceContainerHighest`
- Active color → `MaterialTheme.colorScheme.primary` (already correct)

### 2.6 — Refactor All Remaining Hardcoded Colors

Sweep through `SavingsCard.kt`, `RecitationFeedbackOverlay.kt`, `LoadingSkeleton.kt`, `EmptyState.kt` and replace every `Color(0x…)` with the appropriate scheme or extended-color role.

### 2.7 — Refactor All `RoundedCornerShape` Literals

Global search-replace across `presentation/components/*.kt`:

| Literal | Replacement |
|---------|-------------|
| `RoundedCornerShape(4.dp)` | `MaterialTheme.shapes.extraSmall` |
| `RoundedCornerShape(6.dp)` | `MaterialTheme.shapes.extraSmall` (closest token) |
| `RoundedCornerShape(8.dp)` | `MaterialTheme.shapes.small` |
| `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` |
| `RoundedCornerShape(16.dp)` | `MaterialTheme.shapes.large` |
| `RoundedCornerShape(20.dp)` | `MaterialTheme.shapes.large` (closest) |
| `RoundedCornerShape(24.dp)` | `MaterialTheme.shapes.extraLarge` (closest) |
| `RoundedCornerShape(25.dp)` | `CircleShape` (stadium) |
| `RoundedCornerShape(28.dp)` | `MaterialTheme.shapes.extraLarge` |

---

## Files Changed

| File | Action |
|------|--------|
| `presentation/theme/ExtendedColors.kt` | **Create** |
| `presentation/theme/Theme.kt` | **Modify** — wire `ExtendedColors` via CompositionLocal |
| `presentation/components/MantraComponents.kt` | **Rewrite** — all 4 composables |
| `presentation/components/SavingsCard.kt` | **Modify** — replace colors & shapes |
| `presentation/components/RecitationFeedbackOverlay.kt` | **Modify** — replace colors & shapes |
| `presentation/components/LoadingSkeleton.kt` | **Modify** — replace colors & shapes |
| `presentation/components/EmptyState.kt` | **Audit & modify** |

## Acceptance Criteria

- `grep -rn 'Color(0x' --include='*.kt' presentation/components/` returns **0 results** (excluding `ExtendedColors.kt` and `Color.kt` which are the definition files).
- `grep -rn 'RoundedCornerShape' --include='*.kt' presentation/components/` returns **0 results** (all replaced with `MaterialTheme.shapes.*` or `CircleShape`).
- `grep -rn '\.shadow(' --include='*.kt' presentation/components/` returns **0 results**.
- App looks visually correct in both light and dark themes.

## Estimated Effort

~3–4 hours.
