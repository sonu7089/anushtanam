# Lotus UI review

## Simplified Sandhya counts, 9 September 2026

One filled “Start mental Japa” button per period, aligned dates and observance labels, a clearer draft card, and personal minimums in Settings. Counts now use “mantra” and “mantras.”

| View | Light | Dark |
| --- | --- | --- |
| Period counts | [Open](sandhya-counts-light-simple-counts.png) | [Open](sandhya-counts-dark-simple-counts.png) |
| Resume a draft | [Open](sandhya-draft-light-simple-counts.png) | [Open](sandhya-draft-dark-simple-counts.png) |
| Minimum settings | [Open](settings-minimums-light-simple-counts.png) | [Open](settings-minimums-dark-simple-counts.png) |

[Counter with editable number](sandhya-counter-light-simple-counts.png).

At 320dp with 150% text: [periods](sandhya-counts-light-simple-counts-compact.png), [draft](sandhya-draft-light-simple-counts-compact.png), and [minimum settings](settings-minimums-light-simple-counts-compact.png).

## MF-01: Sandhya counts

The production count controls, with local sample data. Reviewed on API 37 in both themes and at 320dp with 150% text.

| View | Light | Dark | Larger text |
| --- | --- | --- | --- |
| Period counts | [Open](sandhya-counts-light-mf-one.png) | [Open](sandhya-counts-dark-mf-one.png) | [Open](sandhya-counts-light-mf-one-compact.png) |
| Edit total | [Open](sandhya-entry-light-mf-one.png) | [Open](sandhya-entry-dark-mf-one.png) | [Open](sandhya-entry-light-mf-one-compact.png) |
| Paused counter | [Open](sandhya-counter-light-mf-one.png) | [Open](sandhya-counter-dark-mf-one.png) | [Open](sandhya-counter-light-mf-one-compact.png) |

[Unrecorded counts](sandhya-empty-light-mf-one.png). [Implementation and test notes](../mentor-feedback/mf-01-implementation.md).

## Child Google sign-in and direct family linking

The child sign-in page now offers Google. Family connects by QR or by ID and PIN. The QR screenshot uses an inactive local fixture, not a real invitation.

- [Child sign-in, light](child-auth-light-family-google.png) and [dark](child-auth-dark-family-google.png).
- [New child PIN setup](child-google-pin-light-family-google.png).
- [Link Google in child Settings](settings-child-google-light-family-google.png).
- [Parent Family page](family-light-family-google.png).
- [Child Family page](family-child-light-family-google.png).
- [Child QR code, light](family-child-qr-light-family-google.png) and [dark](family-child-qr-dark-family-google.png). The page brings the complete code into view when it opens.

See the [implementation and setup notes](../mentor-feedback/child-google-family-linking.md).

## MF-07: Google sign-in and profile photos

Reviewed on an API 37 emulator with separate app data. These screens use the production components. The Settings pages use sample accounts and show the initial fallback for a missing photo.

- [Adult sign-in, light](parent-auth-light-mf-seven.png) and [dark](parent-auth-dark-mf-seven.png).
- [Link Google to an existing account](settings-link-google-light-mf-seven.png).
- [Linked profile, light](settings-google-light-mf-seven.png) and [dark](settings-google-dark-mf-seven.png).

See [MF-07 implementation and Firebase setup](../mentor-feedback/mf-07-implementation.md) for test results and the remaining live account checks.

## Earlier reviews

Captured from the Android debug build on an API 37 emulator. These are production Compose components with local sample profiles, counts, and targets. No account was created and no cloud data was seeded for the gallery. Practice and recitation show representative component states; the recording screenshot does not represent a live microphone session.

## Navigation, mala, and reflection polish, 6 September 2026

| Screen | Light | Dark |
| --- | --- | --- |
| Home and Material Symbols navigation | [Open](home-light-polished.png) | [Open](home-dark-polished.png) |
| Purashcharana with photographic mala | [Open](purashcharana-light-polished.png) | [Open](purashcharana-dark-polished.png) |
| Reflection reading section | [Open](reflection-light-polished.png) | [Open](reflection-dark-polished.png) |

[Home after scrolling](home-detail-light-polished.png) shows the photographic mala within its existing entry card.

At 320dp and 150% text: [Home](home-light-polished-compact.png), [Purashcharana](purashcharana-light-polished-compact.png), [reflection](reflection-light-polished-compact.png), and [expanded reflection](reflection-light-polished-compact-expanded.png).

Speech: [unknown-support example](voice-check-light-polished.png) and [actual emulator service result](voice-live-light-polished.png). The latter uses the real gate and detects a missing Hindi pack; it does not record audio. See [verification and physical-device limits](../ui-polish-and-speech.md).

## Previous refinement, 6 September 2026

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

## Simplified sign-in and Settings

The latest screens show the sign-in fields, Login, then "or" and Google. The role question, account advice, photo switch and routine sync messages have been removed.

| Screen | Light | Dark |
| --- | --- | --- |
| Parent sign-in | [Open](parent-auth-light-simple-signin.png) | [Open](parent-auth-dark-simple-signin.png) |
| Child sign-in | [Open](child-auth-light-simple-signin.png) | [Open](child-auth-dark-simple-signin.png) |
| Settings with Google | [Open](settings-google-light-simple-signin.png) | [Open](settings-google-dark-simple-signin.png) |
| Link Google | [Open](settings-link-google-light-simple-signin.png) | |

## Home and Settings spacing

The streak sits at the right edge of the daily count, wrapping below it on compact screens. Settings uses a smaller flower area and shows the profile higher on the page without a card border or background. The debug build and four existing Home and Settings UI checks pass.

| Screen | Light | Dark | 320dp, larger text |
| --- | --- | --- | --- |
| Home | [Open](home-light-spacing.png) | [Open](home-dark-spacing.png) | [Open](home-light-spacing-compact.png) |
| Settings with Google | [Open](settings-google-light-spacing.png) | [Open](settings-google-dark-spacing.png) | [Open](settings-google-light-spacing-compact.png) |

[Settings with bottom navigation](settings-light-spacing.png). The compact Home capture is scrolled to show the full streak.
