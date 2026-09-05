# Lotus UI design decisions

## Direction
Anushtanam uses white page backgrounds, neutral paper-like surfaces, muted lotus accents, and botanical sage. Dark mode uses neutral green charcoal, low-elevation surface steps, and soft high-contrast accents. Red is reserved for errors.

## Native Material 3
The app retains the compatible Material 3 1.2.1 / Compose 1.6 toolchain. It uses real Material buttons, cards, switches, chips, navigation, dialogs, and text fields. Shapes and spring motion take inspiration from M3 Expressive. This is not a migration to the newer experimental Expressive API or a Kotlin/toolchain upgrade.

Sources: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Material motion schemes](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme).

## Type
Newsreader gives headings a quiet editorial character. DM Sans keeps forms, navigation, and dense information clear. Fonts are bundled and work offline. Sanskrit uses Android font fallback; the mantra block explicitly uses the system serif family for Devanagari clarity. Text respects system font scaling.

## Layout
Home gives practice and recitation immediate entry points, then groups intentions, family/reminders, and teachings. Welcome uses an airy photographic opening and two clear role actions. Settings uses native, accessible selection chips and grouped rows. Progress prioritizes readable numbers. Practice and japa reserve the center of the screen for the mantra and recording controls. Forms and longer pages scroll; authentication adds IME padding. Paired cards stack on narrow layouts or with larger text. The compact bottom bar uses native 48dp icon buttons with accessible names for every destination, avoiding squeezed text labels.

## Art
Three real bitmap assets replace the procedural brand flower, launcher art, and decorative lamp. The shared `LotusArtwork` component renders bundled PNGs; text is kept off photographs. Functional Material icons and charts remain native. Prompts and licenses are in `artwork.md` and `licenses`.

## Motion and accessibility
Buttons and clickable cards use small, damped spring scale changes. The recording control changes shape gently and displays an explicit stop square. It has no idle animation. Progress rings settle with a critically damped spring and announce the actual value rather than the intermediate frame. Existing fast tab changes and short detail navigation transitions are preserved. Compose honors Android animation scaling.

Buttons use a 56dp minimum height; preference chips have at least a 48dp target. The UI uses theme foreground/background pairs rather than hardcoded white text, and Android system bar contrast follows the selected theme.

## Behavior
No evaluation, authentication, counting, storage, reminder, or cloud-sync rules changed. Routes, callback arguments, target validation, and preferences remain intact. Domain and data logic were moved to separate modules; regression tests cover the existing behavior.
