# Lotus UI review

Captured from the Android debug build on an API 37 emulator. These are production Compose components with local sample profiles, counts, and targets. No account was created and no cloud data was seeded for the gallery. Practice and recitation show representative component states; the recording screenshot does not represent a live microphone session.

## Latest refinement, 6 September 2026

These captures include the new imagery, floating custom navigation, plain-English copy, and completed Sandhya charts. The gallery below them records the previous design pass.

| Screen | Light | Dark |
| --- | --- | --- |
| Home | [Open](home-light-refined.png) | [Open](home-dark-refined.png) |
| Welcome | [Open](welcome-light-refined.png) | |
| Settings | [Open](settings-light-refined.png) | [Open](settings-dark-refined.png) |
| Family | [Open](family-light-refined.png) | |
| Japa | [Open](recitation-light-refined.png) | |
| Sandhya | [Open](sandhya-light-refined.png) | [Open](sandhya-dark-refined.png) |
| Completed Sandhya charts | [Open](sandhya-complete-light-refined.png) | [Open](sandhya-complete-dark-refined.png) |

At 320dp width and 150% text: [Home](home-light-refined-compact.png), [Settings](settings-light-refined-compact.png), [completed Sandhya](sandhya-complete-light-refined-compact.png), and [charts after scrolling](sandhya-complete-light-refined-compact-scrolled.png).

See [verification and scope](../ui-refinement.md).

## Original light theme

| Screen | Screenshot |
| --- | --- |
| Welcome | [Open](welcome-light.png) |
| Home | [Open](home-light.png) |
| Parent sign-in | [Open](parent-auth-light.png) |
| Child sign-in | [Open](child-auth-light.png) |
| Child ID | [Open](child-id-light.png) |
| Settings | [Open](settings-light.png) |
| Practice | [Open](practice-light.png) |
| Recitation | [Open](recitation-light.png) |
| Progress | [Open](progress-light.png) |
| Targets | [Open](targets-light.png) |
| Set target | [Open](set-target-light.png) |
| Family | [Open](family-light.png) |
| Children's progress | [Open](children-light.png) |
| Sandhya | [Open](sandhya-light.png) |
| Purashcharana | [Open](purashcharana-light.png) |
| Voice model setup | [Open](voice-light.png) |
| Empty state | [Open](empty-light.png) |

## Dark theme

[Welcome](welcome-dark.png) · [Home](home-dark.png) · [Settings](settings-dark.png) · [Recitation](recitation-dark.png)

## Compact layout and larger text

320dp wide, 150% system text scaling. Long pages and dialog bodies scroll. Paired cards stack, chips wrap with spacing, and compact navigation keeps all six destinations accessible through labeled icon controls.

[Home](home-light-compact.png) · [Settings](settings-light-compact.png) · [Children](children-light-compact.png) · [Progress](progress-light-compact.png) · [Practice](practice-light-compact.png) · [Set target](set-target-light-compact.png)

## Reproduce

Install the debug APK, then run `scripts/capture-ui.ps1 -Serial <emulator-serial>`. Use `-Dark` for the dark theme, `-Pages home,settings` to select pages, and `-Suffix compact` to name captures made at a different display configuration. Use `-AdbPath` and `-AdbServerPort` when running a specific SDK executable or an isolated ADB server. The script waits for an idle rendered window before capture. It does not change screen size or font scale.

The review activity is debug-only and excluded from the release app. Its bottom tabs navigate between fixtures. Authentication forms retain their real handlers; use those forms only for visual inspection unless you intend to sign in.
