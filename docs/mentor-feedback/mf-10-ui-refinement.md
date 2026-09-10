# MF-10 message screen refinements

Updated 10 September 2026.

## Changes

- Replaced the list's text refresh button with the existing outlined sync icon, alongside **Teachings and messages**.
- The icon shows progress during a request and stays disabled during a 10-second cooldown. Screen-reader labels describe both states.
- The shared repository applies the same cooldown to first-page requests. Repeated taps, overlapping callers, failed fetches, and leaving during a request cannot start another first-page server request within that window. A new screen in the same app process reuses the saved result. Reading earlier pages remains available.
- Removed **See current messages**, **Refresh**, and retry buttons from the detail screen. The top Back control remains. Unavailable and offline copy explains how to return and open a message again.
- Removed the separate Android notification settings button. The notification toggle stays tappable while notifications are off. Turning it on requests permission when Android allows a popup. If it cannot show one, the app opens Android's app or channel notification settings. The switch reflects actual permission on return.
- Added new artwork for the message list. The reading page continues to show the picture chosen for each published message.
- The Home entry now uses that paper-and-jasmine scene above a serif heading, with a separate reading link and arrow. It remains directly below **A moment to reflect**, and the whole card opens the message list.
- Lowered the Messages refresh and Settings sync controls by 8 dp, preserving their 48 dp touch targets and disabled/loading behaviour.
- Replaced connectivity assumptions in both list and detail errors with a simple request to try again in some time. Backend permission and index failures are not described as a lack of internet.

The cooldown is a client behavior for ordinary app use, rather than a server quota for modified clients. It is measured with elapsed time, so changing the phone's date does not skip it. It is retained across navigation within the running app, not across a fresh process launch.

## New artwork

Generated using the built-in imagegen tool. No API key or fallback CLI was used.

Workspace asset: [messages_morning_notes.png](../../frontend/android/src/main/res/drawable-nodpi/messages_morning_notes.png)

The generated scene has handmade paper, jasmine, and a softly lit sandstone ledge. It fades into the existing light and dark backgrounds. The image is decorative and is omitted from screen-reader descriptions.

Final generation prompt:

> Use case: photorealistic-natural. Create one new landscape artwork for the Teachings and messages page in Anushtanam, a quiet Indian mantra practice app. This is a production bitmap background, not a UI mockup. Scene: a small, intimate reading corner in a traditional Indian home at dawn. Two softly curved sheets of warm ivory handmade paper rest on a pale sandstone ledge beside a delicate sprig of white jasmine, with gentle morning light from an unseen window and soft leaf shadows. The paper is blank, without writing or symbols. Natural, believable fine paper and stone textures, understated editorial photography, warm and welcoming, unhurried. Composition: wide 3:2 landscape; paper and jasmine occupy the central-right region, broad calm softly lit space toward the left, all edges soft and uncluttered so a mobile app can fade the image into a cream or dark background. The main subjects remain visible in a wide shallow crop. Palette: ivory, pale sand, soft sage, restrained honey light. No river, no mountains, no people, no hands, no book lettering, no text, no logos, no icons, no borders, no UI, no exaggerated spiritual glow. High quality natural photographic detail with a gentle atmospheric softness.

Android permission handling follows the platform's [runtime permission guidance](https://developer.android.com/training/permissions/requesting). A permission refusal keeps the effective preference off. Merely opening Android settings cannot grant notification permission.

## Verification

- Debug APK and instrumentation APK built successfully.
- Data unit suite: 92 tests passed, including four repository cooldown tests.
- App unit suite: 79 tests passed.
- Android lint: 0 errors, with the existing 14 warnings and 3 hints.
- Emulator: all seven message UI and notification tests passed, including the refresh icon's disabled state, absence of detail-page actions, a tappable blocked notification toggle, and large-text reading.
- Actual Android permission flow checked on the emulator: toggle opened the permission popup; declining kept it off; a further attempt opened Android notification settings; enabling there and returning set the app toggle on.
- Light and dark list artwork, reading-page controls, and Settings placement reviewed visually.

Screenshots:

- [Message list](../ui-review/messages-light-mf-ten-refined.png)
- [Dark message list](../ui-review/messages-dark-mf-ten-refined.png)
- [Reading page](../ui-review/message-detail-light-mf-ten-refined.png)
- [Notification toggle](../ui-review/settings-messages-light-mf-ten-refined.png)
- [Android permission popup](../ui-review/messages-permission-popup-mf-ten-refined.png)
- [Android settings fallback](../ui-review/messages-permission-settings-mf-ten-refined.png)

Logs: `mf10-refinement-build.log`, `mf10-refinement-final-build.log`, and `mf10-refinement-ui-tests.log`. The root `app-debug.apk` is the updated build.

### Home and alignment follow-up

The updated debug build and instrumentation build passed. The seven message UI and notification checks passed again. Lint reports no errors, with the same 14 warnings and 3 hints. Reviewed Home and Messages in both themes, the neutral loading error, and the lower Settings sync control.

- [Home in light mode](../ui-review/home-messages-light-mf-ten-polished.png)
- [Home in dark mode](../ui-review/home-messages-dark-mf-ten-polished.png)
- [Message list](../ui-review/messages-light-mf-ten-polished.png)
- [Dark message list](../ui-review/messages-dark-mf-ten-polished.png)
- [Loading error](../ui-review/messages-offline-light-mf-ten-polished.png)
- [Settings sync placement](../ui-review/settings-sync-light-mf-ten-polished.png)

Follow-up logs: `mf10-home-polish-build.log` and `mf10-home-polish-ui-tests.log`. The root `app-debug.apk` contains these changes. No Firebase deployment or live message publication was performed. Firebase setup and loading-error troubleshooting are in section 7 of the website integration guide.

### Compact Backup and Sync spacing

The backup heading and status now share a text column beside the sync control. This removes the blank space caused by centring the short heading inside the icon's 48 dp row. The card uses 16 dp vertical padding, and the notification preference, backup card, and sign-out action use 16 dp gaps. The sync control retains its 48 dp touch target and existing behaviour.

The debug build passes, with no lint errors and the existing 14 warnings and 3 hints. Build log: `mf10-settings-spacing-build.log`.

- [Compact backup card](../ui-review/settings-sync-light-mf-ten-compact.png)
- [Compact backup card in dark mode](../ui-review/settings-sync-dark-mf-ten-compact.png)

The message scheduler has also changed to every 30 minutes, or 48 runs per day. The exported function configuration was checked and all nine message policy and workflow tests passed (`backend/firebase/mf10-half-hour-tests.log`). Scheduled sending can wait until the next half-hourly run; the app's manual refresh and japa deferral behaviour are unchanged.

### Message card refinement

The list cards now bring the date and serif title together beside a small preview of the message's chosen artwork. A short summary leads into a divided footer with the author and **Read message**. The entire card remains tappable. Pinned messages retain their **A note to keep close** label.

The cards use 20 dp padding and 16 dp corners. Summaries show up to three lines; the reading page still holds the full message. Decorative pictures are hidden on narrow layouts or with larger text, and the author and reading link can wrap onto separate rows. The page banner, Home entry, reading page, Settings, and delivery behaviour were not changed in this follow-up.

Debug and instrumentation builds and lint passed. All seven message UI and notification checks passed again. The actual test message was reviewed in light and dark mode and at 180% text size, with the emulator's original text size restored afterward.

- [Test message card](../ui-review/messages-phone-light-mf-ten-note.png)
- [Test message card in dark mode](../ui-review/messages-phone-dark-mf-ten-note.png)
- [Pinned and ordinary messages](../ui-review/messages-light-mf-ten-note.png)
- [Message card with larger text](../ui-review/messages-phone-light-mf-ten-large-text-card.png)

Logs: `mf10-message-card-build.log` and `mf10-message-card-tests.log`. The root `app-debug.apk` contains the refined cards.
