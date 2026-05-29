# Design Decisions — Anushtanam MD3 Theme

This document details the theme customization and deliberate departures from the baseline Material Design 3 specification in the **Anushtanam** application.

---

## 1. Color System

### Saffron Seed Color
- **Seed**: `#FF6F00` (Deep Saffron)
- **Rationale**: Chosen for its cultural, traditional, and spiritual significance in Vedic recitation practices. 
- **Application**: The seed is used to generate the light and dark color schemes via standard Material 3 color utility logic.

### Harmonized Extended Colors
- Standard MD3 color schemes do not include semantic success (green) or warning (amber) roles in the main color scheme.
- We defined custom `ExtendedColorScheme` configurations:
  - **Success**: `#386A20` (Light) / `#9CD67E` (Dark), representing positive evaluation (threshold met).
  - **Warning**: `#7C5800` (Light) / `#F5BE48` (Dark), representing borderline accuracy or areas for phonetic improvement.
- These colors are harmonized with the Deep Saffron seed to maintain visual compatibility across themes.

### Avatar Gradients
- `ProfileAvatar` uses horizontal/vertical gradients spanning primary/secondary/tertiary colors and warning states. 
- These colorful gradients act as brand identifiers and personal visual signatures for user profiles.

---

## 2. Typography

- **Font Family**: Default system fonts (Roboto/Roboto Flex) optimized for readability in Hindi (Devanagari script) and transliterated Sanskrit text.
- **Display and Headline Weights**: Display titles use extra bold and heavy weights to give the Sanskrit verse displays a solid, premium, and structured look.

---

## 3. Shapes & Surfaces

- **Elevation**: Leverages MD3 tonal elevation (`surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`) to build visual hierarchy. Baseline drop shadows are completely avoided in favor of solid contours and outline borders (`outlineVariant`).
- **Rounded Corners**:
  - Main action buttons (`MantraButton`) use `CircleShape` (stadium shape) to feel friendly and tactile.
  - Interactive cards (`MantraCard`, `TargetCard`) use `MaterialTheme.shapes.large` (16dp).
  - Dialogs and bottom sheets use `MaterialTheme.shapes.extraLarge` (28dp).

---

## 4. Motion & Transitions

- **Navigation Transitions**: Employs Emphasized Shared-Axis transitions (`EmphasizedEasing`) with a duration of 500ms for natural side-to-side navigation flow.
- **Micro-Animations**:
  - `ProgressRing` uses a standard ease-in-out curve over 800ms for smooth recitation counts.
  - The recording microphone button (`MicButton`) uses infinite linear pulsing animation rings to indicate active audio tracking.
