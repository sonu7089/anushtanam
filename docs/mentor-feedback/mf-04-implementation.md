# MF-04: Gayatri practice guide

Implemented 9 September 2026.

The permanent Home card sits between Your Goal and Purashcharana, as requested by the app owner. It uses a dedicated open-book and mala illustration, with the existing card surfaces, typography, and colours. The same illustration appears inside the guide. All new copy uses simple English without em dashes.

## Behaviour

- The guide explains Vaikhari, Upamsu, and Manasika in a scrollable bottom sheet. It attributes the recommendation for mental japa to the mentor and encourages a steady pace.
- Opening Japa or Sandhya for the first time shows the current guide version. Home's Japa action reaches this same route. Later visits keep a compact guide entry near the start controls.
- Dismissal, the close button, and Continue for now leave the guide unacknowledged. A gentle review link remains after dismissal. Reading the guide is never a condition for counting.
- Only I have read this records acknowledgement. The Home card and page links remain after acknowledgement.
- The guide is bundled and works offline. Japa's guide entry also remains available when voice setup is needed. Opening the guide during spoken japa stops listening first.
- DataStore records first display, deliberate opening, and explicit acknowledgement separately for each local profile and content version. The open sheet survives activity recreation. An acknowledgement write failure offers retry or dismissal, and a delayed write for one profile cannot close another profile's guide.
- Increment `PracticeGuideViewModel.VERSION` for materially revised teaching. A new version gets its own first-use and acknowledgement record without erasing the old version.

## Scope

The existing Japa tab still counts spoken repetitions. Sandhya provides the mental counter and later count entry. The guide accurately describes those routes; changing the Japa tab's counting mode belongs to MF-03. The six-caution verse remains MF-05. No widget is implemented yet; future entry points should reuse the same guide state. Acknowledgement sync is optional in the specification and is not added here.

The simple Upamsu wording follows the proposed product description. Mentor editorial approval is not represented as completed. Display, opening, and acknowledgement do not establish understanding.

## Verification

- Debug app and Android test APKs built successfully.
- All 77 app unit tests passed, including six new guide tests for dismissal, acknowledgement, recreation, profile isolation, retries, and duplicate taps.
- Two on-device DataStore tests passed for persistence, distinct timestamps, profile isolation, and new guidance versions.
- All three guide Compose tests passed at 320dp with 150% text. They cover Home discovery, all three methods, skipping, acknowledgement, reopening, failed saves, and immediate close. All 12 existing Home and UI regression tests also passed at the normal screen size.
- Android lint passed with no errors. Existing warnings remain.
- Visually reviewed the Home placement and guide in light and dark themes, plus a 320dp screen at 150% text size.

The emulator's original Android profile has an existing database migration failure involving a duplicate `stableId` column. UI checks use a separate temporary Android user with fresh app data; the original data is preserved. This change introduces no Room migration.

## Screenshots

- [Home placement](../ui-review/home-light-mf-four.png)
- [Guide, light](../ui-review/practice-guide-light-mf-four.png)
- [Guide, dark](../ui-review/practice-guide-dark-mf-four.png)
- [Guide, larger text](../ui-review/practice-guide-light-mf-four-large-text.png)
- [Guide actions, larger text](../ui-review/practice-guide-light-mf-four-large-text-bottom.png)
