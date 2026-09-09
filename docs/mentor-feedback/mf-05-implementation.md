# MF-05: Verse, six cautions and a guide people can return to

Implemented 9 September 2026, including the app owner's request to remove the acknowledgement system introduced in MF-04.

## Behaviour and content

- The guide opens only when selected from Home, Japa, Sandhya or Settings under Help with your practice. Automatic first-use display, review prompts, reading-status storage and the I have read this button are removed.
- Daily mental-japa advice remains visible. Three expandable sections contain the three methods, the mentor-supplied verse and all six cautions. The sections use the existing app artwork, colours, rounded surfaces and typography.
- The Sanskrit verse and IAST transliteration retain the supplied text and line breaks. They wrap at narrow widths and can be selected. Attribution is explicitly the source shared by the mentor; no independently verified edition or verse number is claimed.
- The English uses short, gentle instructions without em dashes. All six Sanskrit terms are included. The text distinguishes prescribed Vedic accents from adding a tune, keeps written learning aids available, and directs interpretation of text-only learning and low tone to the reader's teacher. It does not turn the low-tone caution into a command to chant louder or abandon mental japa.
- The quality descriptions are explained without labelling readers or their devotion. The guide links to the existing pronunciation-practice route and invites readers to learn the meaning with their teacher. No new mentor-reviewed meaning resource is claimed.
- The guide works offline. Closing is available at the top and bottom. Section buttons announce expanded/collapsed state and support larger text. Reading position and expanded sections survive activity recreation; changing profile or logging out closes the guide.
- The obsolete guide preferences implementation and its acknowledgement tests are removed. Existing preference files from older installs are no longer read or written. No Room migration is needed.

The English explanation is implemented from the supplied feedback and proposed contextual treatment. Mentor editorial review, including the low-tone and text-only-learning interpretations, remains a release review item; this change does not claim that review has happened.

## Verification

- Debug APK and Android test APK built successfully.
- All 76 app unit tests passed, including guide recreation, delayed profile loading, profile switching and logout.
- All four guide Compose tests passed at 320dp width and 150% text. They cover opening from Home and Settings, closing and reopening without acknowledgement, all three methods, the verse and six cautions, expansion/collapse, section restoration and the pronunciation action.
- Android lint passed with no errors (13 warnings and 3 hints).
- Verified the exact supplied Sanskrit, presence of all six terms and absence of em dashes in the guide resources.
- Visually reviewed light and dark layouts and expanded content, including Sanskrit and IAST wrapping with larger text.

UI verification used a hidden, read-only API 37 emulator and a temporary local Android user. Existing app data was preserved. No cloud changes or release publication were performed.

## Screenshots

- [Guide in light mode](../ui-review/practice-guide-light-mf-five.png)
- [Guide in dark mode](../ui-review/practice-guide-dark-mf-five.png)
- [Expanded cautions](../ui-review/practice-guide-cautions-dark-mf-five.png)
- [Guide with larger text](../ui-review/practice-guide-light-mf-five-large-text.png)
- [Verse with larger text](../ui-review/practice-guide-verse-mf-five-large-text.png)
