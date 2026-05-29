# Design Decisions — Anushtanam MD3 Theme

## Color
- **Seed Color**: #FF6F00 (Deep Saffron) — chosen for cultural significance.
- **Extended Colors**: success (green) and warning (amber) harmonized with seed.
- **Avatar Gradients**: preserved as brand identity (not from dynamic scheme) to keep user visual distinction clear.

## Typography
- **Inter Font**: standard Material 3 typography with Outfit/Inter pairing.
- **Hindu-Devanagari Display**: specialized sizing (large, clear Devanagari fonts) to ensure easy readability of complex Sanskrit ligatures.

## Shape
- **Expressive Shapes**: Buttons use `CircleShape` (stadium shape) for premium, modern, expressive feeling.
- **Cards**: medium and large rounded corner shapes (`MaterialTheme.shapes.large`) for smooth, cohesive visual boundaries.

## Motion
- **Splash Screen**: Emphasized decelerate easing at 400ms.
- **Navigation Transitions**: Shared-axis transitions at 500ms using `EmphasizedEasing` to feel smooth and tactile.
- **Celebration Overlay**: Enter transition uses emphasized decelerate to build positive behavioral reinforcement.

## Accessibility & Contrast
- **Minimum Touch Targets**: All interactive elements (e.g. `MantraButton`, profile items, tab navigation) adhere to the minimum 48dp × 48dp guidelines.
- **System Contrast Adaptability**: On Android 14+ (API 34), static and dynamic color schemes automatically respond to system high-contrast requests by boosting text-to-background contrast ratios.
