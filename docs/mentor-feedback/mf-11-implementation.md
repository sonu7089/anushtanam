# MF-11: A moment to reflect

11 September 2026.

The owner's accepted direction is to use the existing **A moment to reflect** area. There is no new Home panel. The separate Teachings and messages entry stays below it.

## Reader experience

The mentor's current reflection appears first, with the existing heading, text styles, accent line, and dividers. A short reflection shows its full body. For a body over 600 characters, Home shows its summary. Read more opens the existing message reading screen. Attribution appears only when supplied.

Next note moves to the built-in notes and eventually returns to the mentor's reflection. The reader's place survives screen state restoration and is not reset by an incoming revision. No read or dismissal events are uploaded. This is a local browsing choice, not a permanent dismissal across visits.

Home has no loading, refresh, scheduling, or connection labels for this content. The app loads a saved reflection first, then checks the server on entry and foreground return, with a shared ten-second cooldown. It does not wait for this fetch to show practice or counts. A valid saved reflection remains usable offline. Missing, expired, unsupported, or unpublished content falls back to the built-in notes. Expiry is checked while Home remains open. An offline phone learns about removal or correction after its next successful check.

The permanent practice guide, its acknowledgement, counts, reminders, and notification preference are independent of this reflection.

## Publishing contract

Use the existing MF-10 callable API and published schema with the fixed content ID **`home-reflection`**. This ID identifies the one mentor reflection shown on Home. Do not create a new ID each week. Other messages and pinned announcements cannot replace it.

The existing backend already supports save, publish, schedule, immutable revisions, restore, cancellation, and unpublish for this ID. No Firebase function, rule, index, or deployment change is required. A direct document read avoids a feed query or extra index. The reflection also appears in the existing message list, including on MF-10 clients.

The separate website needs an **A moment to reflect** editor that always uses this ID. See [the website integration guide](mf-10-website-guide.md#mf-11-a-moment-to-reflect). This repository does not contain that website.

One new Android release enables the Home integration. After installation, the mentor can change supported text through the website without another APK. Sample review text is local debug content and is not published to Firebase.

## Verification

- Android debug APK and instrumentation APK built successfully. Final APK: `app-debug.apk` at the repository root.
- Data unit suite: 99 tests passed, including seven new reflection tests covering cached-first reads, network timeout, removal, revisions, malformed/expired/future content, cooldown, and cancellation.
- App unit suite: 79 tests passed.
- Home, reflection, and practice-guide instrumentation: 20 tests passed on `emulator-5580`. Includes Read more routing, all-note cycling, delayed arrival while browsing, removal fallback, and long content with 1.8 font scale in a 320 dp layout.
- Backend policy and workflow suite: ten tests passed, including monthly publication, revision, restoration, and removal of `home-reflection` without a campaign or notification.
- Android lint: zero errors; 14 existing warnings and three hints. The final APK and lint were rebuilt after a visibility-clock adjustment so a reflection published during a fetch can appear immediately.
- Visual review: [light](../ui-review/reflection-mentor-light-mf-eleven.png), [dark](../ui-review/reflection-mentor-dark-mf-eleven.png), and [large text](../ui-review/reflection-mentor-large-mf-eleven.png). These show local sample content, not a published mentor note.
- Live read-only Firebase check: the exact public `publishedContent/home-reflection` endpoint returns `404 NOT_FOUND`, rather than a permission error. No mentor reflection exists yet. No production content, campaigns, rules, or functions were changed.

Logs: `mf11-build.log`, `mf11-final-build.log`, `mf11-ui-tests.log`, and `backend/firebase/mf11-backend-tests.log`. The separate website editor and its first live publish cycle remain to be completed in the website project. Until then, built-in notes appear in the new app.
