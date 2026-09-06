# Lotus UI design decisions

## Direction
Anushtanam uses white page backgrounds, neutral paper-like surfaces, muted lotus accents, and botanical sage. Dark mode uses neutral green charcoal, low-elevation surface steps, and soft high-contrast accents. Red is reserved for errors.

## Native Material 3
The app retains the compatible Material 3 1.2.1 / Compose 1.6 toolchain. It uses real Material buttons, cards, switches, chips, navigation, dialogs, and text fields. Shapes and spring motion take inspiration from M3 Expressive. This is not a migration to the newer experimental Expressive API or a Kotlin/toolchain upgrade.

Sources: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3), [Material motion schemes](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme).

## Type
Newsreader gives headings a quiet editorial character. DM Sans keeps forms, navigation, and dense information clear. Fonts are bundled and work offline. Sanskrit uses Android font fallback; the mantra block explicitly uses the system serif family for Devanagari clarity. Text respects system font scaling.

## Layout
Home gives practice and recitation immediate entry points, then groups goals, family/reminders, and practice notes. Welcome uses an airy photographic opening and two clear role actions. Settings uses native, accessible selection chips and grouped rows. Progress prioritizes readable numbers. Practice and japa reserve the center of the screen for the mantra and recording controls. Forms and longer pages scroll; authentication adds IME padding. Paired cards stack on narrow layouts or with larger text. The compact bottom bar uses native tab semantics with accessible names for every destination and touch targets of at least 48dp.

## Art
Eight bitmap assets provide botanical scenes, a family moment, a river at dawn, a lamp, a photographic mala, and the brand emblem. `AmbientArtwork` extends scenes into the page gutters and fades them into the active theme background. Headings begin at the opaque end of the fade; body copy stays below it. The lamp and jasmine decorate the page directly. The mala surrounds the existing lotus on Home and Purashcharana. Navigation uses six standard Material Symbols Rounded, compiled into native VectorDrawables; functional charts remain native. Prompts and licenses are in `artwork.md` and `licenses`.

The floating bottom bar uses a rounded surface and a stationary selection background that fades in place over 140ms. All existing routes, restored tab state, and parent/child destination differences are retained. Labels are shown where they fit; compact screens and larger text use accessible named tabs with at least 48dp touch targets at 320dp width. The visual reference is the floating tab bar described in [Apple's new software design](https://www.apple.com/newsroom/2025/06/apple-introduces-a-delightful-and-elegant-new-software-design/). This is a native Compose implementation, without an iOS Liquid Glass rendering engine.

Home's reflection is an open reading section with a serif heading, a quiet sage rule, a note count, and explicit Read more and Next note actions. It keeps the daily starting note and all eight existing notes. Longer passages expand within the page.

Settings no longer displays the Advanced Vedic option. Its stored preference and evaluation logic are preserved. Interface copy uses plain English, short explanations for Sanskrit practice terms, and no em dashes. Practice feedback gives a specific next step; goals and streaks avoid guilt-based language.

## Motion and accessibility
Buttons and clickable cards use small, damped spring scale changes. The recording control changes shape gently and displays an explicit stop square. It has no idle animation. Progress rings settle with a critically damped spring and announce the actual value rather than the intermediate frame. Existing fast tab changes and short detail navigation transitions are preserved. Compose honors Android animation scaling.

Buttons use a 56dp minimum height; preference chips have at least a 48dp target. The UI uses theme foreground/background pairs rather than hardcoded white text, and Android system bar contrast follows the selected theme.

## Behavior
No evaluation, authentication, counting, storage, reminder, or cloud-sync rules changed. Routes, callback arguments, target validation, and preferences remain intact. Domain and data logic were moved to separate modules; regression tests cover the existing behavior.
