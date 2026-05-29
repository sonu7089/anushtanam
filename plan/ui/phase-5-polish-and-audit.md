# Phase 5 — Polish: Accessibility, Motion, Contrast & Compliance Audit

> **Goal**: Final polish pass addressing accessibility, MD3 motion spec, high-contrast support, and running a full compliance audit to verify the overhaul meets MD3 standards.
>
> **Prerequisite**: Phases 1–4 complete.

---

## Tasks

### 5.1 — Accessibility Sweep

#### Touch Targets
MD3 requires **minimum 48dp × 48dp** touch targets for all interactive elements.

Audit checklist:
- [ ] `NavigationBarItem` — handled by MD3 component ✅
- [ ] `MantraButton` — verify height ≥ 48dp (currently 50dp ✅, but check width on narrow layouts)
- [ ] `ProfileAvatar` when clickable (login grid) — 72dp ✅
- [ ] `MicButton` (practice/recitation) — verify size
- [ ] Icon buttons in Settings — verify `IconButton` used (48dp default)
- [ ] Chips / badges — non-interactive, but if interactive ensure 48dp

#### Content Descriptions

```kotlin
// Every Icon must have contentDescription
Icon(
    imageVector = Icons.Outlined.Mic,
    contentDescription = stringResource(R.string.cd_microphone) // not null
)

// Decorative icons can use null
Icon(
    imageVector = Icons.Filled.Circle,
    contentDescription = null // truly decorative
)
```

Audit all `Icon()` calls for missing `contentDescription`:
```
grep -rn 'contentDescription = null' --include='*.kt' presentation/
```

Review each `null` to verify it's genuinely decorative.

#### Semantic Properties

Add `Modifier.semantics` for custom composables that don't inherit MD3 roles:

```kotlin
// ProgressRing — announce progress to TalkBack
Box(
    modifier = modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = animatedProgress,
            range = 0f..1f,
        )
        contentDescription = "${ (animatedProgress * 100).toInt() }% complete"
    },
    // …
)
```

### 5.2 — Color Contrast Verification

MD3 tonal pairing guarantees WCAG contrast in standard mode, but verify:

| Check | Target | Tool |
|-------|--------|------|
| Normal text (< 18sp) on surface | ≥ 4.5:1 | Android Lint / manual |
| Large text (≥ 18sp or ≥ 14sp bold) on surface | ≥ 3:1 | Android Lint |
| UI component boundaries (outline on surface) | ≥ 3:1 | Manual |
| `onPrimary` on `primary` | ≥ 4.5:1 | Guaranteed by Theme Builder |
| `onPrimaryContainer` on `primaryContainer` | ≥ 4.5:1 | Guaranteed |
| Extended colors (success/warning) on their containers | ≥ 4.5:1 | Manual — these are custom |

Actions:
- Run Android Lint with accessibility checks enabled.
- For extended colors (`success`, `warning`), manually verify contrast ratios using an online contrast checker.

### 5.3 — High Contrast Support

MD3 supports 3 contrast levels. On Android 14+ (API 34), the system provides a contrast preference.

```kotlin
// In Theme.kt — check system contrast preference
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
        // dynamic schemes automatically respect system contrast on API 34+
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // …
}
```

For static schemes, consider providing medium/high contrast variants if the app targets visually impaired users.

### 5.4 — MD3 Motion Alignment

Verify all animations use the correct MD3 easing:

| Animation Type | Easing | Duration |
|---------------|--------|----------|
| Screen enter (navigation) | Emphasized decelerate | 400ms |
| Screen exit (navigation) | Emphasized accelerate | 200ms |
| Shared-axis (forward/back) | Emphasized | 500ms |
| `ProgressRing` value change | Standard | 300ms |
| Splash scale-in | Emphasized decelerate | 400ms |
| Celebration overlay enter | Emphasized decelerate | 400ms |
| FAB / component interactions | Spring-based (Compose Material3 MotionScheme) | Spring params |

Verify `Motion.kt` from Phase 3 is used consistently across all screens.

### 5.5 — File Header Consistency

Per AGENTS.md, every Kotlin file must start with:

```kotlin
/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
```

Currently some files say "Mantra Siddhi" and some say "Anushtanam". Normalize to "Anushtanam" everywhere.

```
grep -rn 'Mantra Siddhi' --include='*.kt'
```

### 5.6 — Run Full MD3 Compliance Audit

Using the audit procedure from `SKILL.md`, run a comprehensive audit and score each category:

| Category | Expected Score After Phase 5 |
|----------|------------------------------|
| Color tokens | 9/10 (all roles from Theme Builder, dynamic color) |
| Typography | 8/10 (correct scale, intentional weight departures documented) |
| Shape | 9/10 (centralized in `Shapes`, all references via `MaterialTheme.shapes`) |
| Elevation | 9/10 (tonal elevation, no shadows for depth) |
| Components | 8/10 (MD3 Button/Card/Dialog/ListItem, custom where necessary) |
| Layout | 7/10 (Scaffold on all screens, but no multi-pane adaptive layout yet) |
| Navigation | 8/10 (bottom bar with icon differentiation, Scaffold integration) |
| Motion | 8/10 (MD3 easing constants, spring motion via MotionScheme) |
| Accessibility | 8/10 (touch targets, contrast, semantics, content descriptions) |
| Theming | 9/10 (dynamic + static, light + dark, extended colors) |

**Target overall score**: ≥ 83/100 (all categories pass ≥ 7)

### 5.7 — Document Design Decisions

**New file**: `docs/design-decisions.md`

Document all intentional departures from MD3 baseline:

```markdown
# Design Decisions — Anushtanam MD3 Theme

## Color
- Seed: #FF6F00 (Deep Saffron) — chosen for cultural significance
- Extended colors: success (green) and warning (amber) harmonized with seed
- Avatar gradients: preserved as brand identity (not from dynamic scheme)

## Typography
- Display/Headline weights: [document if keeping heavier weights]

## Shape
- Buttons use extraLarge (stadium) — more expressive than default full-round

## Motion
- Splash: emphasized decelerate at 400ms
- Navigation: shared-axis emphasized at 500ms
```

---

## Files Changed

| File | Action |
|------|--------|
| All `presentation/**/*.kt` | **Audit** — contentDescription, touch targets, semantics |
| `presentation/theme/Theme.kt` | **Modify** — contrast support notes |
| `presentation/theme/Motion.kt` | **Verify** — all easings used consistently |
| All `presentation/**/*.kt` | **Modify** — file header normalization |
| `docs/design-decisions.md` | **Create** — design documentation |

## Acceptance Criteria

- MD3 Compliance Audit overall score ≥ 83/100.
- All categories score ≥ 7 ("pass" threshold).
- Zero `grep` hits for `Color(0x` outside of `Color.kt` and `ExtendedColors.kt`.
- Zero `grep` hits for `RoundedCornerShape` outside of `Shape.kt`.
- Zero `grep` hits for `.shadow(` in presentation layer.
- All `contentDescription` values are either meaningful strings or explicitly null (decorative).
- File headers say "Anushtanam" consistently.
- `docs/design-decisions.md` exists and documents all deliberate departures.

## Estimated Effort

~2–3 hours (audit and cleanup, no new features).

---

## Full Overhaul Summary

| Phase | Focus | Effort | Status |
|-------|-------|--------|--------|
| 1 | Theme Foundation (Color, Shape, Type, Spacing) | 2–3h | ⬜ |
| 2 | Shared Components (hardcoded colors, shadows, shapes) | 3–4h | ⬜ |
| 3 | Screen Refactoring (Scaffold, TopAppBar, edge-to-edge, motion) | 5–6h | ⬜ |
| 4 | MD3 Component Upgrades (Button variants, Card variants, Dialog, ListItem) | 3–4h | ⬜ |
| 5 | Polish (Accessibility, contrast, motion, audit, documentation) | 2–3h | ⬜ |
| **Total** | | **~15–20h** | |

Each phase builds on the previous. Do not skip phases — the later phases depend on the theme infrastructure from Phase 1 and component cleanup from Phase 2.
