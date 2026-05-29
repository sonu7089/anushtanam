# Phase 1 — Theme Foundation: Color Scheme, Typography & Shape System

> **Goal**: Replace the ad-hoc saffron/gold color scheme and incomplete `ColorScheme` mapping with a fully-specified MD3 theme generated from a saffron seed color, add proper `Shapes` object, support dynamic color on Android 12+, and clean up `Type.kt`.

---

## Current State Audit

### Color (`Color.kt` + `Theme.kt`)

| Problem | MD3 Rule Violated | Severity |
|---------|-------------------|----------|
| Only 9 of 29+ color roles populated in `lightColorScheme` / `darkColorScheme` | All roles must be set for predictable component rendering | 🔴 Critical |
| `background` role used instead of `surface` for screen backgrounds (MD3 recommends `surface` as the primary background) | `surface` is the new default background | 🟡 Medium |
| No `primaryContainer`, `secondaryContainer`, `tertiaryContainer`, `surfaceContainer*`, `outline`, `outlineVariant`, `inverseSurface`, etc. | Components like `NavigationBar`, `Card`, `FilterChip`, `Dialog` pull from these roles — currently they fall back to Material defaults that don't match the saffron palette | 🔴 Critical |
| Hardcoded `LightAccent` / `DarkAccent` mapped to `tertiary` without tonal generation | Breaks tonal pairing guarantee | 🟡 Medium |
| No dynamic color support (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) | MD3 on Android 12+ should default to dynamic, with static as fallback | 🟡 Medium |

### Typography (`Type.kt`)

| Problem | MD3 Rule Violated | Severity |
|---------|-------------------|----------|
| Custom `fontWeight` overrides on several scales (e.g., `displayLarge` is `Bold` — MD3 spec says `Regular 400`) | Should match MD3 baseline or deliberately document departure | 🟡 Medium |
| Correct font sizes and line heights — ✅ passes | — | ✅ Pass |
| No custom font family loaded (uses `FontFamily.SansSerif` = Roboto on Android) — this is actually correct for MD3 | — | ✅ Pass |

### Shape

| Problem | MD3 Rule Violated | Severity |
|---------|-------------------|----------|
| **No `Shapes` object at all** — `MaterialTheme` called without `shapes` parameter | Components use Material defaults, but custom components like `MantraCard` hardcode `RoundedCornerShape(16.dp)` everywhere | 🔴 Critical |
| 40+ instances of `RoundedCornerShape(N.dp)` with inconsistent values (4, 6, 8, 12, 16, 20, 24, 25 dp) | Must centralize in `MaterialTheme.shapes` and use `MaterialTheme.shapes.medium`, `.large`, etc. | 🔴 Critical |

### Elevation

| Problem | MD3 Rule Violated | Severity |
|---------|-------------------|----------|
| `MantraCard` uses `.shadow(4.dp, …)` — MD3 cards should use tonal elevation, not shadows | Tonal elevation is the primary depth cue in MD3 | 🟡 Medium |
| `ProfileAvatar` uses `.shadow(4.dp, CircleShape)` | Same issue | 🟡 Medium |

---

## Tasks

### 1.1 — Generate Full Color Scheme from Saffron Seed

**Seed color**: `#FF6F00` (Deep Saffron — the app's existing primary)

Use [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/) to export a full Kotlin theme, or generate programmatically via `material-color-utilities` and translate to Kotlin `Color(0xFF…)` values.

**File**: `presentation/theme/Color.kt`

```kotlin
// ─── Light scheme (from seed #FF6F00) ───
// Primary group
val md_theme_light_primary = Color(0xFF8B5000)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFFFDCBE)
val md_theme_light_onPrimaryContainer = Color(0xFF2C1600)

// Secondary group (auto-generated harmonious)
val md_theme_light_secondary = Color(0xFF735B40)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFFFDCBE)
val md_theme_light_onSecondaryContainer = Color(0xFF29180B)

// Tertiary group
val md_theme_light_tertiary = Color(0xFF586339)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFDCE8B4)
val md_theme_light_onTertiaryContainer = Color(0xFF161F00)

// Error group (static, not derived from seed)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)

// Surface group (5 container levels + dim/bright)
val md_theme_light_surface = Color(0xFFFFF8F5)
val md_theme_light_onSurface = Color(0xFF211A13)
val md_theme_light_onSurfaceVariant = Color(0xFF51453A)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFFFF1E7)
val md_theme_light_surfaceContainer = Color(0xFFFFEBDC)
val md_theme_light_surfaceContainerHigh = Color(0xFFF8E0D0)
val md_theme_light_surfaceContainerHighest = Color(0xFFF2DAC9)

// Outline
val md_theme_light_outline = Color(0xFF837568)
val md_theme_light_outlineVariant = Color(0xFFD7C3B3)

// Inverse
val md_theme_light_inverseSurface = Color(0xFF372F27)
val md_theme_light_inverseOnSurface = Color(0xFFFDEEE3)
val md_theme_light_inversePrimary = Color(0xFFFFB86C)

// ─── Repeat for dark scheme ───
// (export both from Theme Builder)
```

> **Important**: The exact hex values above are illustrative. **You MUST export from Material Theme Builder** with seed `#FF6F00` to get a harmonious, contrast-safe set. Copy the generated Kotlin file verbatim.

**Acceptance criteria**:
- All 29+ color roles populated in both `lightColorScheme(…)` and `darkColorScheme(…)`.
- No leftover `LightPrimary`, `DarkAccent`, etc. top-level vals — replace with `md_theme_light_*` / `md_theme_dark_*` naming.

### 1.2 — Add Dynamic Color Support

**File**: `presentation/theme/Theme.kt`

```kotlin
@Composable
fun AnushtanamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // … rest unchanged
}
```

**Acceptance criteria**:
- On Android 12+ devices, UI adapts to wallpaper colors by default.
- Static saffron scheme used on API < 31.
- Rename function from `MantraSiddhiTheme` → `AnushtanamTheme` (matches app name).

### 1.3 — Create `Shape.kt` with MD3 Shape Tokens

**New file**: `presentation/theme/Shape.kt`

```kotlin
val AnushtanamShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Chips, snackbars
    small = RoundedCornerShape(8.dp),        // Text fields, menus
    medium = RoundedCornerShape(12.dp),      // Cards
    large = RoundedCornerShape(16.dp),       // FABs, nav drawer
    extraLarge = RoundedCornerShape(28.dp),  // Dialogs, bottom sheets
)
```

Wire into `MaterialTheme(shapes = AnushtanamShapes, …)`.

**Acceptance criteria**:
- `Shapes` object used in `MaterialTheme`.
- All custom components reference `MaterialTheme.shapes.medium`, `.large`, etc. instead of inline `RoundedCornerShape(N.dp)`.

### 1.4 — Correct Typography Weights

**File**: `presentation/theme/Type.kt`

Align `fontWeight` values with the MD3 baseline type scale:

| Scale | MD3 Baseline Weight | Current Weight | Fix |
|-------|---------------------|----------------|-----|
| `displayLarge` | Regular (400) | Bold | → `Normal` |
| `displayMedium` | Regular (400) | Bold | → `Normal` |
| `headlineLarge` | Regular (400) | SemiBold | → `Normal` |
| `headlineMedium` | Regular (400) | SemiBold | → `Normal` |
| `titleLarge` | Regular (400) | Bold | → `Normal` |
| `titleMedium` | Medium (500) | SemiBold | → `Medium` |

> **Note**: If you **intentionally** want heavier headings for the spiritual/premium brand feel, that's a valid design choice. But document it clearly with a comment like `// Intentional departure: heavier weight for brand emphasis`. The key is to be deliberate, not accidental.

### 1.5 — Add Spacing Constants

**New file**: `presentation/theme/Spacing.kt`

```kotlin
object AnushtanamSpacing {
    val none = 0.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}
```

These follow the MD3 8dp grid system. Use throughout all screens to replace magic padding/margin numbers.

---

## Files Changed

| File | Action |
|------|--------|
| `presentation/theme/Color.kt` | **Rewrite** — full 29+ role scheme from Theme Builder |
| `presentation/theme/Theme.kt` | **Modify** — dynamic color, rename function, add shapes |
| `presentation/theme/Type.kt` | **Modify** — fix weights to MD3 baseline |
| `presentation/theme/Shape.kt` | **Create** — MD3 shape tokens |
| `presentation/theme/Spacing.kt` | **Create** — 8dp grid spacing constants |

## Dependencies

None — this phase is purely additive. No new libraries needed. `dynamicLightColorScheme` / `dynamicDarkColorScheme` are already in `androidx.compose.material3`.

## Testing

- **Visual**: Launch app on API 31+ emulator → verify dynamic color adapts to wallpaper.
- **Visual**: Switch to API 30 emulator → verify static saffron scheme.
- **Visual**: Toggle system dark mode → verify dark scheme.
- **Unit**: No unit tests required for theme definitions.

## Estimated Effort

~2–3 hours (mostly Theme Builder export + wiring).
