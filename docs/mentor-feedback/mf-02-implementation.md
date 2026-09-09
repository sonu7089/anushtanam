# MF-02 implementation

MF-02 is implemented locally and the debug APK builds. The app owner confirmed that the new Firebase rules were deployed on September 9, 2026. A two-device check against the live backend remains to be done.

## What changes for a practitioner

- Saved Sandhya japa adds to the eligible Gayatri Sankalpam and Purashcharana. Each repetition counts once in daily and lifetime history.
- A goal stops displaying progress at its target. Extra japa stays in history. Correcting an overshoot does not reduce a goal until the eligible total falls below its target.
- Corrections stay with the original goals. An older correction cannot replace a newer active target or reopen a withdrawn vow.
- Saving can show a quiet completion or milestone message. A correction below a completed goal gets a short explanation. Replaying a save does not repeat the message.
- Home and Progress use performed japa for their count totals. Pronunciation scores, accuracy trends, and learning sessions keep their own data.
- Parent-assigned rewards still require accepted audio recitations. The count sheet explains this.

The new text uses simple English and no em dashes. The layout and existing images remain in place. No new artwork was needed. Light and dark count sheets and the Progress card were visually checked in the existing emulator review profile.

## Accounting and storage

Room v12 adds `japa_entries` and stable goal identities, enrollment dates, lifecycle fields, and preserved legacy count baselines. A Sandhya save commits the period total, change history, contribution revisions, goal projections, draft removal, and account-bound outbox writes in one transaction.

Each addition has its own stable ID. An absolute decrease reduces the newest contributions in that period first. An absolute increase revises the last contribution and keeps its original goal links. Use Add more or the counter to record new practice. The practice date and timezone stay attached to each contribution.

Accepted NORMAL audio sessions use the same local projection path. The old increments have been removed from the evaluation flow. PRACTICE sessions do not create japa contributions. Audio remains separate from the manual Sandhya period total, so editing that total does not rewrite an audio assessment.

Goal arithmetic sums uncapped quantities with `BigInteger` before applying the goal cap. Daily and lifetime displays also retain large totals. A period remains limited to `Long`; a save or restore that exceeds that limit rolls back instead of wrapping.

Existing goal progress becomes a baseline. Existing MF-01 Sandhya history stays editable and remains in performed totals. It is not automatically imported into goals during the upgrade. The old personal-target schema did not record enrollment dates, so historical eligibility cannot safely be inferred. Legacy audio sessions still on the phone remain in performed history. Old cloud learning summaries are not converted into invented japa entries.

## Sync

New entries and goals use private `users/{uid}/japaEntries` and `japaGoals` collections. Goal baselines are established once. Parent reward collections are not used for manual japa.

Entry revisions decide which correction wins. If two devices edit the same revision, the later edit timestamp wins, followed by the operation ID when timestamps match. Concurrent edits replace one entry; they are not added together. Independent additions have different IDs and combine. Accepted cloud revisions are retained in an audit subcollection.

Uploads use the durable outbox. Restore runs after uploads, at sign-in, and when the app returns to the foreground. Responses are paginated. Local merging, period rebuilding, and goal recalculation commit together. A remote change invalidates an older open editor rather than silently overwriting its base. Account checks surround network and database work.

MF-08 still owns a dedicated backup status and conflict-history interface, broader legacy restore work, and restore performance improvements. New-entry sync code and rules are present here. The app owner confirmed the rules deployment. A two-device check against the live backend has not been performed.

## Verification

- 344 domain tests, 72 data tests, and 71 app tests passed.
- 14 focused Android database tests passed, including migration, the 740 example, corrections after overshoot, goal replacement, withdrawal, ownership, mantra eligibility, audio separation, two offline replicas combining to 708, stale snapshot replay, and rollback.
- 25 Firebase emulator tests passed, including private entry access, decreases, revision checks, fixed allocations, immutable baselines, and existing family rules.
- Debug APK assembled successfully. Screenshots are linked in the UI review gallery.
