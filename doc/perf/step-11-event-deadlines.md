# Step 11 — Event and deadline maintenance

Implemented and locally verified 12 September 2026. Covers UI-07, DB-09, RW-09/10 and POLL-01/02/04/06.

## Notification registration and preferences

`TeachingNotifications` uses a conflated process-owned IO worker. Registration is keyed by UID, token, effective permission/app opt-out and app version; installation identity and the currently supported `en` locale are fixed for that worker. The token is reused until FCM sends a replacement. Identical inputs are reused only after Firestore acknowledges the write. Failed submissions remain retryable on the next permission/auth/token/resume event, and Firestore retains its offline transport retry behavior. Requests arriving during a write retain the latest desired state.

Channel creation runs once per instance. Unchanged permission refreshes do not reschedule delivery or clear the queue. Permission choices use asynchronous preference persistence; installation-file access and delivery-job maintenance run on IO. `pending()` reads one preference snapshot and commits only if expired/malformed records were removed. Durable receipt storage and the commit-before-posting seen marker remain in place. No notification payload, account identifier or token is added to diagnostic counters.

## Reminder ownership and calculations

The verified-profile startup request now starts the process-owned scheduler input stream. It observes effective settings, permission changes and observed-period identities. Metadata, count drafts, permission-prompt bookkeeping and minimum-count preferences do not re-arm alarms. Disabled reminders have no log subscription. Screen ViewModels only save settings/observance; the scheduler owns their resulting alarm updates. The notification action receiver still requests reconciliation when it is running without a foreground profile producer.

A desired-alarm cache suppresses identical replacements. The existing boot, package-replacement and time/timezone receiver explicitly forces reconciliation. Account/current-settings checks, delivered-stage/date checks, period/stage identities and cancellation on sign-out remain. A failed reconciliation keeps observing subsequent input changes and also permits a retry on the next verified-profile startup request.

Each reconciliation shares one log snapshot across all six period/stage combinations. Visible Sandhya reuses its already-collected log list, so deadline changes do not query logs again. Solar epochs are cached by date, latitude, longitude and resolved zone (bounded to 32 entries), and all stages share each computed day. Sandhya caches displayed solar/adherence projections, combines drafts afterward, and stops its screen producers immediately when unsubscribed. Selected historical dates survive a hidden/resumed screen and reset on account replacement.

## Calendar and visibility deadlines

`CalendarTime` publishes at actual deadlines and restarts waits after invalidation. Day projections use the next local midnight, including 23/25-hour daylight-saving days. Application time/date/timezone broadcasts and Main Activity resume invalidate the clock. Home's japa, dashboard daily count/streak and Progress's japa dates, overview streak and bounded score range consume the day signal. Recorded entry dates are unchanged. Device zone remains the Home/Progress policy; Sandhya uses its selected region zone with the existing device-zone fallback.

Sandhya status wakes at its next reminder-policy transition or region midnight, and delivery metadata changes invalidate the projection. Late preparation uses the two-minute eligibility cutoff rather than repeatedly waiting for the policy's synthetic “now + one second” alarm time.

Messages (including detail), Home reflection and family QR use one lifecycle-aware deadline helper. It handles publication as well as expiry boundaries, cancellation on content replacement, resume and system clock changes. Expiry waits run only while RESUMED. Server QR validation remains authoritative.

## Verification

- **627 unit tests pass:** 367 domain, 143 data and 117 app. New regressions cover concurrent registration coalescing/acknowledgement/failure, all registration inputs, exact expiry, forward/backward clock jumps, cancelled collectors, DST midnight, Home/Progress date rollover, one shared reminder-log read, cached solar calculations, 20 draft edits plus two idle minutes, hidden subscriptions and selected-date retention.
- **19 Pixel 7 Pro device tests pass:** deadline publication/expiry/replacement/background-resume, notification queue persistence and deduplication, zero commits for 20 unchanged permission refreshes and 20 empty maintenance calls, independent reminder stages, immutable notification destinations, count-editor/accessibility behavior, and explicit alarm reconciliation. The opt-in `ReminderMaintenanceTest` verifies five equal reconciliation requests add zero alarm writes and an explicit forced reconciliation writes all six, without modifying the saved settings.
- Debug and optimized benchmark APKs build. Android lint has **zero errors and 14 warnings**. The initial sandboxed build could not read the installed SDK; subsequent authorized SDK builds passed. An existing notification assertion read Android's asynchronous notification list too early; both notification tests now wait up to five seconds for acknowledgement. The final complete device suite passes.
- Final build logs: `build/step11-complete-build.log` (data/app unit checks, debug/benchmark/device-test builds and lint), with the domain run in `build/step11-verified-build.log`. Final instrumentation: `build/step11/device-final.txt`. Final debug startup reached `perf_home_ready`.

### Physical optimized-build maintenance comparison

Same Pixel, signed-in profile, New Delhi region, both reminder stages enabled, and the same reminder-settings page. The device clock/timezone and display configuration were retained. Reminder master was originally off and was restored after verification. Raw snapshots and alarm logs are under `build/step11/`; [aggregate counters and APK hashes](step-11-results.json) retain the actual measurements.

| Unchanged Settings interval | Previous optimized build | Step 11 measured build |
| --- | ---: | ---: |
| Observed interval | 79.396 s | 87.878 s |
| Alarm replacements | 6 | 0 |
| Sandhya log queries | 6 | 0 |
| Process CPU during interval | 1,145 ms | 663 ms |

The updated process retained one registration write, one scheduler-owner update and six initially armed alarms throughout its idle interval and three subsequent background/resume cycles. No additional upcoming-reminder or solar computations occurred. The three unrelated `room.SELECT.other` reads in each interval are retained in the result file rather than attributed to reminder work.

These are single observations with unequal intervals, not a controlled battery or UI-latency benchmark. The measured APK precedes the final failure-retry ownership guard; the steady-state maintenance path is unchanged. The final build's forced/unchanged reconciliation behavior is covered by the physical-device test above. Midnight and clock jumps use deterministic tests; the phone's actual date/time was not changed. The complete paired Perfetto/release matrix remains step 13.

## Limits

The work counters establish removed maintenance, not a universal battery or frame-time speedup. This step does not change speech/watchdog/playback timing. No database schema or cloud rules change is required. The production-rule deployment and two-physical-phone recovery matrix already listed in earlier steps remain separate release requirements.
