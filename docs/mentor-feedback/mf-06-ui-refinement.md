# MF-06 UI refinement

Implemented locally on 9 September 2026, following the app owner’s additional feedback.

- Home has an illustrated Sandhya Vandan card immediately above the existing Gayatri Mantra Practice Guide and Purashcharana cards. The card opens the existing Sandhya page.
- Family links and children’s previews are removed from Home. Family is a bottom tab for parent and child profiles. Parents can open children’s progress from this tab, including its existing individual progress and target controls. Child profiles retain their linking, approval and QR features. Individual profiles retain five tabs.
- Main Settings no longer contains a practice guide card. The Home guide and preparation-notification guide destinations remain available.
- The reminder upgrade message and its Got it button have been removed, including their unused preference and strings.
- Battery and sound settings actions have outlined buttons with 16 dp horizontal and 12 dp vertical padding.
- The bottom-of-page reminder summary now has period/date headings and compact time columns. One selected stage fills a single row. Both disabled hides the entire card. Each stage retains its own upcoming date, including when preparation and event belong to different days. At larger text sizes the columns stack.

## Verification

Debug and Android-test APK builds succeeded. All 79 presentation unit tests passed. All 20 emulator UI checks passed across LotusUiTest, PracticeGuideUiTest and SandhyaReminderUiTest. These cover the revised Home destinations, Family tab selection, individual-profile tab count, Home guide opening and pronunciation action, Settings navigation, stage filtering, next-day display and disabled reminders. Reminder switches are exercised at 320 dp and 1.5x text.

UI review images use debug fixtures. They verify layout, not live alarm delivery or signed-in cloud linking. The existing phone delivery verification requirement remains in the main MF-06 implementation note.

- [Home cards, light](../ui-review/home-cards-light-refined.png)
- [Home cards, dark](../ui-review/home-cards-dark-refined.png)
- [Reminder summary, light](../ui-review/sandhya-next-reminders-light-refined.png)
- [Reminder summary, dark](../ui-review/sandhya-next-reminders-dark-refined.png)
- [Reminder controls](../ui-review/settings-reminders-light-refined.png)
- [Family tab](../ui-review/family-light-refined.png)
- [Reminder summary, large text](../ui-review/sandhya-next-reminders-light-refined-large-text.png)
- [Battery button, large text](../ui-review/settings-battery-light-refined-large-text.png)

## Generated artwork

Generated with the built-in image generation tool, preserving transparency. Project asset: [sandhya_vessel.png](../../frontend/android/src/main/res/drawable-nodpi/sandhya_vessel.png). The generated original remains in Codex’s generated_images directory. No existing artwork was replaced.

Final prompt:

> Create a refined, photorealistic cutout illustration for a warm Indian spiritual practice mobile app: one small traditional polished copper lota water vessel with a gently rounded body and open rim, beside a simple slender copper ritual spoon angled gracefully at its base. A few tiny water droplets, restrained warm dawn light reflected in the copper, natural soft highlights, inviting and peaceful. Three-quarter view, complete objects visible, centered compact composition, no clutter. The style should resemble premium editorial still-life photography of prayer objects, with subtle soft shadows immediately underneath. Genuine transparent background, no floor, no scenery, no frame, no text, no letters, no symbols, no people. Clear silhouette legible as a small mobile card illustration. Warm copper and muted gold palette, understated and authentic.

Final debug APK: `app-debug.apk` at the repository root. SHA-256: `47F6F197D6E53E81A973E52E3A10DD3E1D972CE836EB460357158EB0B0F2D249`. The corrected guide-removal assertion also passed in a targeted rerun.

## Finishing touches

The Settings entry now reuses the copper vessel artwork. Dates in the reminder summary share the period label’s baseline and have the same 14 dp trailing inset as the time-block content.

Reflection titles and full note text now advance to the next note when tapped. The explicit Next note button remains, aligned to the end of the reading area. There is no collapse control or truncated text. Touch feedback and the existing size animation are preserved. The accessible action is named Next note.

Both APKs built successfully. Five focused UI checks passed, including cycling through every reflection with alternating title taps, text taps and button clicks, and confirming Show less never appears. Reviewed the actual Settings, reminder and reflection layouts.

Current previews: [Settings artwork](../ui-review/settings-light-finishing.png), [date alignment](../ui-review/sandhya-next-reminders-light-finishing.png), [reflection](../ui-review/reflection-light-finishing.png).

Latest root APK SHA-256: `374893A432F3B51FA6F4EA24DED0E0DB01B6AC35B7CA88EB229AA518D945941A`.

## Unified reflection interaction

The reflection title, full text and Next note cue now sit inside one tappable component with shared touch feedback. The cue is no longer a separate button. The focused emulator check confirms exactly one accessible click action and cycles through all notes by tapping the title, body or cue. Debug and test APK builds passed.

Latest root APK SHA-256: `7A07843AE598F9F97134E6052DDC574EE8DC09E2D360AEAE436D3778C89FCA80`.
