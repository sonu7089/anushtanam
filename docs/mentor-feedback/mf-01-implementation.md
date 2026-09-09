# MF-01: Sandhya Gayatri counts

Implemented 8 September 2026. Simplified 9 September 2026 following app-owner feedback.

- Record, add, or correct counts for each period. Choose an earlier date to correct history.
- Each period has one filled “Start mental Japa” button. Tap the counter number to type a count. “Edit total” remains for corrections.
- Count labels use “mantra” and “mantras.” Date and observance actions share the same trailing edge and text baseline, including after marking observed.
- Settings lets each profile choose its three minimums. Defaults are 108, 32, and 64. These choices update the cards, counter, and entry presets without changing recorded counts.
- The draft card now says “Continue your japa.” The early/late reminder note has been removed.
- The counter supports pause, resume, undo, and +108. Drafts stay on the phone until saved or discarded. Their date stays fixed unless changed deliberately.
- Observance and quantity are separate. Old observed records still show “Count not recorded.” Zero and counts below the minimum are accepted.
- A save updates the period total, adds a revision to its history, and removes its draft in one Room transaction. Replaying the same operation does not add the count again. Stale edits require another review before saving.

Room v11 adds `sandhya_counts`, `sandhya_count_changes`, and `sandhya_count_drafts`. Counts use `Long`; the day summary handles totals beyond one `Long`. Each operation keeps its profile, date, period, timezone, previous total, and stable ID. No audio sessions or scores are created.

Goal credit and new-entry sync are implemented in [MF-02](mf-02-implementation.md). The broader backup and restore interface remains MF-08. These counts are saved locally; the save message says so. Reminder scheduling remains MF-06.

## Verification

- The revised debug APK builds. The latest 21 focused checks pass: 14 ViewModel tests, six Compose tests, and a device preferences test.
- Domain and ViewModel tests cover arithmetic, invalid input, overflow, duplicate saves, profile changes, failed saves, fixed draft dates, recovery, and absolute corrections.
- Android database tests cover the 740 total, correction to 640, explicit zero, old observance, replay, rollback, draft recovery after reopening the database, and migrations from exported schemas to v11.
- Compose tests cover direct entry, disabled save, paused counters, separate observance, baseline alignment before and after observation, personal minimum entry, and actions at 320dp with larger text.
- [Visual review](../ui-review/README.md): light, dark, and 320dp with 150% text.

The API 37 emulator uses a separate local user, `MF01Review`, because its original user has an experimental v10 database that does not match this repository's v10 schema. That data was preserved. No cloud account or microphone was needed for these checks.
