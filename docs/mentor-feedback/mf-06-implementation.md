# MF-06: Sandhya reminders

Implemented locally on 9 September 2026. The app owner's updated instructions take precedence over the original reminder setup proposal.

## Settings organization follow-up

The app owner requested a separate **Settings > Sandhya Vandan** page. It now contains region search/current location, minimum mantra counts, and all reminder controls and delivery help. Main Settings contains one entry to this page. The main Sandhya page has only a settings shortcut in its top bar.

“Next Sandhya reminders” is a read-only card at the bottom of the main Sandhya page, below the progress charts. It is hidden when the master switch is off or both stages are off. Only enabled stages appear, each with a full date and time. It uses the scheduler's next-occurrence calculation: past/delivered stages and occurrences marked observed advance to a future date, including tomorrow. A historical date selected for count review does not change the upcoming schedule.

The user-facing “Approximate timing” label and explanation were removed. Alarm scheduling behavior is unchanged by this UI update.

Follow-up verification: debug and Android-test APKs built, all 79 presentation unit tests passed, and 10 emulator UI tests passed (four reminder/settings checks and six existing count/minimum controls). The new checks cover the dedicated page entry and back action, absence of inline controls in main Settings, removal of timing copy, next-day date display, preparation-only/event-only filtering, and hiding the summary when reminders are off. Current previews are linked in the [UI gallery](../ui-review/README.md).

## What changed

- The Sandhya Vandan settings page contains the master reminder switch and separate “15 minutes before” and “At Sandhya time” switches. Both stages default on, while the existing master choice is preserved. Turning reminders off and on keeps the chosen stages.
- Sandhya links to these Settings controls. There is no test-notification button.
- The dedicated settings page shows notification availability, a permission/settings action, sound/channel settings and optional battery troubleshooting. The main Sandhya page shows upcoming reminders calculated from the saved location and timezone. Permission and channel state refresh when returning from Android settings.
- The existing Lotus theme, card styling, spacing and type are reused. The Home entry now uses newly generated copper vessel artwork, matching the practice guide and Purashcharana cards.

## Messages and destinations

Preparation example: “Sayam Sandhya is at 6:20 PM. Take a little time to get ready. Tap to read the practice guide.” Tapping the notification or “Read the guide” opens the existing practice guide.

Event example: “It is time for Sayam Sandhya. When you finish, you can record your count here.” Tapping the notification or “Record count” opens the corresponding date and period in the count editor. If delivery is over two minutes late, the message says “Sayam Sandhya was at 6:20 PM” instead.

The preparation message uses the actual event time rather than promising that exactly 15 minutes remain when Android delivers it late. New user-facing strings use simple English without em dashes.

Notification intents carry their profile, date, period and stage, use immutable PendingIntents, and enter MainActivity through its existing authentication gate. Warm launches are handled by onNewIntent. A different active profile gets an explanatory message. The guide waits for its matching profile before opening. A draft for another date or period is preserved, with a message asking the user to finish or discard it first. Opening a reminder never credits japa or marks an occurrence observed.

## Scheduling and recovery

The scheduler keeps separate preparation and event alarms for all three periods, replacing the old single-stage alarms. Preparation is scheduled 15 minutes before the solar event. Late enablement keeps today's event alarm, offers one immediate preparation cue when more than two minutes remain, and otherwise skips preparation.

Each received alarm rearms its stage before delivery work. Reboot, clock/timezone changes, location changes, package replacement, app startup and return from Settings rebuild eligible alarms. The saved region is sufficient for offline scheduling.

Persistent delivery history is scoped to profile, period, date and stage. It prevents ordinary retries and location changes from repeating a delivered stage. A delayed preparation alarm becomes the event notification when that stage is enabled. Both stages use one visible notification per period, so the event replaces preparation. Dismissing preparation does not cancel the event alarm. Already observed occurrences, old dates and alarms delayed more than one hour after the event are suppressed. Recording a count alone does not suppress reminders.

Alarm payloads also carry the scheduling region/timezone and owner, so stale queued alarms cannot post for a newly selected profile or location. Debug logs include requested schedule, receiver time, posting acceptance and rearm errors without user names or coordinates.

The implementation retains inexact AlarmManager scheduling without a timing status label in the UI. It adds no exact-alarm permission. Android can delay these alarms, including during battery-saving conditions. See [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms) and [notification permission guidance](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Verification

- Debug APK and Android test APK built successfully with the installed Android SDK.
- 506 JVM tests passed: 350 domain, 77 data and 79 Android presentation tests.
- New coverage includes the 15-minute offset, independent stages, late enablement, stale/coalesced delivery, disabled reminders, observed suppression, next-day planning, delivery deduplication after location changes, profile ownership and draft preservation.
- Four targeted tests passed on the Lotus Android emulator: independent controls at 320 dp and 1.5x text, blocked notification help without a test button, actual preparation/event/late notification content and distinct immutable PendingIntents, and destination payload parsing.
- The notification test waits for Android to publish the notification before inspecting it. An initial immediate-read failure was a test synchronization issue and passed after this was corrected.
- Reviewed Settings captures in light, dark and blocked states. Example dates/times in the debug review fixture are illustrative, not delivery measurements.

Screenshots: [light](../ui-review/settings-reminders-light-mf-six.png), [dark](../ui-review/settings-reminders-dark-mf-six.png), [blocked](../ui-review/settings-reminders-blocked-light-mf-six.png), [large text](../ui-review/settings-reminders-light-mf-six-large-text.png).

## Device verification still required

The mentor's original phone failure has not been reproduced or diagnosed. Emulator posting tests and planning tests do not establish real-world alarm delivery. Before closing the delivery defect, record actual preparation/event arrival times on the mentor's phone or an equivalent device, including idle/screen-off, recents dismissal, process death, offline operation, reboot and consecutive days without reopening. Exercise denied/revoked permissions, blocked channels and cold/warm notification taps through the real signed-in flow. Force-stop recovery requires reopening the app; this implementation does not claim to bypass Android's force-stop restriction.
