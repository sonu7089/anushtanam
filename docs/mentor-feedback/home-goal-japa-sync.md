# Home goal credit and tile artwork

Implemented on 9 September 2026. The owner confirmed that goals should include unclaimed Gayatri japa recorded earlier on their creation day, and continue across days until completed.

## Behavior

A self-set goal now includes eligible unclaimed entries from earlier on its first calendar day. The stored recording timezone determines that day. Entries credited to an older goal retain their allocation, so a replacement goal cannot reuse them. Other mantras, profiles and previous-day practice are excluded. Existing parent reward eligibility remains unchanged.

Goal creation projects the existing japa entries in the same Room transaction and queues changed allocations for sync. Reading goals repairs missing eligible allocations in existing data. Later count changes and restored history use the same projection. Repeated repairs do not change the count or create more entries. Quantity corrections continue to revise their original contribution and can reverse completion. Uncertain legacy records with no recording timestamp are not reinterpreted.

Home keeps the latest current goal visible at 100% after completion, labelled Goal complete, until it is replaced or set aside. Today’s japa remains the amount performed today; a goal spanning several days retains the accumulated total.

The Practice and Japa tiles reuse the existing book and mala images at 8% opacity, behind the text and excluded from accessibility announcements. Tile actions and foreground colors are preserved.

## Verification

- Debug, app test and data test APKs built successfully.
- 507 JVM tests passed: 351 domain, 77 data and 79 presentation.
- 11 Room/emulator accounting tests passed, covering earlier same-day mental and audio japa, idempotent repair, overshoot and corrections, continuation into the next day, timezone boundaries, other profiles/mantras, replacement goals, parent-reward eligibility, stale sync snapshots and migration preservation.
- The Home destination UI check passed.
- Reviewed [light Home](../ui-review/home-light-goal-sync.png) and [dark Home](../ui-review/home-dark-goal-sync.png). These use review fixtures; accounting behavior was tested against Room, not inferred from screenshot counts.
- Live cross-device cloud transport was not exercised; restored payload allocation and stale snapshot handling were tested locally.

Root APK SHA-256: `4A0A32ADA66E1A2BE07E096FD41D7EBE1214081C7BBE9416212C096515134C62`.
