MF-08 update, 10 September 2026: [automatic backup and recovery implementation](mf-08-implementation.md).

# Mentor feedback: app change plan

Prepared: 6 September 2026. Updated: 7 September 2026 with the app owner's follow-up. Status: proposed work; app behavior has not been changed by this documentation task.

This plan translates the mentor's feedback into changes to Anushtanam, based on a review of the current Android, domain, Room, and Firebase code. Religious guidance below is attributed to the mentor. It is not presented as a universal rule across traditions or as a scientific claim.

## Read these documents in order

1. **This document:** scope, priorities, current gaps, delivery order, and decisions for review.
2. [Product requirements and acceptance criteria](mentor-feedback-product-spec.md): the screens, flows, counting rules, teaching content, reminders, Google sign-in, and widget.
3. [Implementation and verification plan](mentor-feedback-implementation-plan.md): affected code, data design, migrations, synchronization, engineering tasks, and tests.
4. [Remote notifications and Home content](remote-content-and-notifications.md): developer publishing controls, announcement delivery, dynamic Home cards, and the new release stage.

All checkboxes describe future work. Priority is a recommendation, not an assertion that the mentor assigned it.

Implementation update, 9 September 2026: [MF-04 practice guide and verification](mf-04-implementation.md). The app owner placed its Home card between Your Goal and Purashcharana.

## Main product change

Make daily Gayatri japa usable without speaking into the phone. A practitioner should be able to repeat mentally, record the actual number completed during each Sandhya, and see that number contribute to their eligible Gayatri goals and Purashcharana. Pronunciation learning remains available as a clearly identified practice activity.

The mentor's example should work end to end: **108 in Pratah + 32 in Madhyahnika + 600 in Sayam = 740 Gayatri repetitions for the day**, with 740 eligible contributions to each applicable goal. These are different views of the same 740 repetitions; they must never become 1,480 in lifetime totals.

## Feedback coverage and priorities

| ID | Change | Priority | Origin | Definition of done |
| --- | --- | --- | --- | --- |
| MF-01 | Add actual Gayatri counts to every Sandhya period | P0 | Explicit mentor request | Implemented locally: stepper, direct entry, counter, and corrections. [Details](mf-01-implementation.md). |
| MF-02 | Credit Sandhya japa to Sankalpam and Purashcharana | P0 | Explicit mentor request | Implemented locally with goal accounting, corrections, and revision-based sync. Firebase rules deployment confirmed by the app owner. [Details](mf-02-implementation.md). |
| MF-03 | Make mental japa a first-class flow with persistent guidance | P0 | Mentor guidance and app-owner follow-up | Japa works without microphone/voice-model requirements; a permanent Home guide and visible Japa/Sandhya reminder keep the teaching discoverable after onboarding. |
| MF-04 | Explain Vaikhari, Upamsu, and Manasika before starting and later | P0 | Mentor request and app-owner follow-up | App-owner update, 9 September: guide opens on request, with permanent entry points and no acknowledgement or review prompts. See MF-05. |
| MF-05 | Include the supplied verse and six cautions | P1 | Explicit mentor request | Implemented 9 September: expandable verse and six cautions, simple English and Settings Help access; acknowledgement system removed at app-owner request. Mentor editorial review remains a release item. [Details](mf-05-implementation.md). |
| MF-06 | Remind 15 minutes before and at Sandhya | P0 | Mentor request and app-owner update, 9 September | Implemented locally: all configuration in Settings > Sandhya Vandan, upcoming reminders below the main page charts, preparation tap opens the guide, event tap opens the matching count entry. Physical-device delivery verification remains open. [Details](mf-06-implementation.md). |
| MF-07 | Add Google sign-in and profile photo | P1 | Explicit mentor request | Sign-in, account linking, name/photo fallback, sign-out, and existing-account preservation work. |
| MF-08 | Make saved japa recoverable across devices | P1; correctness foundation is P0 | Mentor's easy-sync request | New count records and corrections survive sync and restore; the app states what is and is not backed up. |
| MF-09 | Add an Android home-screen widget | P1 | Explicit mentor request | Shows the selected goal and remaining count and opens the correct mantra directly. |
| MF-10 | Let developers send teachings and messages remotely | P1 / Release C | Explicit app-owner follow-up | An authorized admin can preview, test, send or schedule optional announcements; users control the notification category. |
| MF-11 | Remotely manage Home content without an app update | P1 / Release C | Explicit app-owner follow-up | Published teachings/messages appear in supported Home cards, with scheduling, offline fallback, unpublish, and revision controls. |
| EX-01 | Show reminder availability and upcoming times | P0 companion to MF-06 | App-owner update, 9 September | Delivery help is in Settings > Sandhya Vandan. Upcoming dates/times appear below the main Sandhya charts for enabled stages only. No test-notification button. |
| EX-02 | Recover an interrupted counter and support corrections | P0 companion to MF-01 | Recommended addition | Process death does not silently lose a draft; editing does not duplicate goal credit. |
| EX-03 | Show a daily breakdown and clear sync status | P1 | Recommended addition | Users can understand their count by Sandhya period and identify pending uploads. |
| EX-04 | Add optional preparation checklist, personal targets, and pacing cues | P2 | Recommended addition | Useful aids remain optional and never manufacture completed repetitions. |
| EX-05 | Add export and an optional private reflection | P2 | Recommended addition | Users can keep a portable count history and optional notes without being forced to share. |

P0 is the first usable release for the mentor's daily practice. P1 completes the requested experience. P2 is optional polish after the requested changes are stable.

The fragment “3.15min notice” is interpreted as “3. 15-minute notice,” supported by the mentor's later explicit request for 15 minutes. It is not interpreted as a 3.15-minute interval.

## What the app already has, and what must change

| Area | Finding in current source | Required change |
| --- | --- | --- |
| Sandhya history | `SandhyaLog` and its Room entity store period, date, notification/observance state, and timestamps; no japa quantity. | Add count contributions linked to the occurrence and show the actual count alongside observance. |
| Mark as done | `SandhyaViewModel.markObservedToday` and `SandhyaActionReceiver` mark observance only. | Keep observance and quantity separate; add a count-entry action. Never silently credit a minimum. |
| Japa route | `MantraNavGraph` wraps both Practice and Recitation in `VoiceModelGate`; normal recitation starts a listening loop. | Route mental japa around microphone and voice-model requirements. |
| Purashcharana | `EvaluateRecitationUseCase` credits only accepted `NORMAL` sessions; `Purashcharana` comments explicitly reject manual entries. | Replace this restriction with explicit count provenance and eligibility rules. Update code comments and user-facing explanations together. |
| Bulk quantities | `IncrementPurashcharanaUseCase`/DAO add one at a time; milestone logic checks equality with a milestone. | Add a quantity-aware path and detect milestones crossed by a batch. Preserve the full performed count when a goal is filled. |
| Goal identity | `Target` currently has no `mantraId`; Purashcharana does. | Make eligible mantra and stable goal identity explicit before allocating new contributions. |
| Reminders | `SandhyaScheduler` already uses `AlarmManager.setAndAllowWhileIdle`; receivers rearm after firing, reboot, and clock/timezone changes. | Investigate the reported device behavior. A new foreground timer is not the remedy. |
| Reminder timing | `SandhyaContract.LEAD_MINUTES` and `SandhyaViewModel.GetSandhyaAdherenceLead` both equal 5; one alarm identity exists per period. | Add preparation (-15 minutes) and event-time (0 minutes) stages with distinct alarm identities and a shared occurrence. |
| Notification navigation | `SandhyaNotifier` opens the app launcher rather than a specific Sandhya occurrence. | Carry validated account/profile, date, and period context into the intended screen. |
| Authentication | Firebase email/password for parents and ID/PIN for children; local profiles use `avatarIndex`. | Add Google credentials and optional photo data without creating duplicate histories or removing current sign-in choices. |
| Synchronization | Room v9 has a durable, account-scoped outbox for progress, targets, Purashcharana snapshots, and child goals. Sandhya logs are local; detailed recitation history is not synced. | Extend the existing outbox and restore contract to the new count history. Google sign-in alone does not implement this. |
| Widget | No AppWidget/Glance implementation found in application sources or dependencies. | Add widget provider, configuration, state, navigation, and update handling. |
| Developer push | `AppFirebaseMessagingService` routes messages through `GoalNotifier`; the optional Cloud Function sends new child goals using one token on the user document. | Add an announcement message type/channel, installation-level registrations, preferences, and authorized campaign dispatch. |
| Home teachings | `SadhanaWisdom` uses bundled strings and a daily rotation. | Keep a permanent practice guide and bundled fallback; add a separately managed remote-content area and publishing workflow. |
| Documentation baseline | `README.md` and `architecture.md` still describe database v7; source and `deferred-cloud-sync.md` describe v9. | Use source/schema v9 as the current migration baseline and reconcile older documentation during implementation. |

The reminder defect has **not** been reproduced on the mentor's device in this task. Source confirms an existing background mechanism, not successful delivery on that device. See the [code map](mentor-feedback-implementation-plan.md#1-current-code-map) for source links.

## Recommended delivery order

### Release A: daily practice and dependable counting

- [ ] Agree on the guidance copy and count-allocation defaults below.
- [ ] Implement a durable quantity-based count model, corrections, and account ownership.
- [ ] Add Sandhya count entry, mental japa, draft recovery, and goal projections.
- [ ] Keep the three japa methods in the guide, available on request from Home, Japa, Sandhya and Settings Help. Do not track acknowledgement.
- [ ] Implement Sandhya reminder setup with permissions/settings, preparation and event-time reminders, diagnostics, and occurrence-specific navigation.
- [ ] Extend or version the existing signed-in sync path before uploading the new counts; do not pass editable quantities through the current monotonic Purashcharana merge unchanged.
- [ ] Pass count, migration, reminder, accessibility, and offline acceptance tests.

The counting model and reminder investigation can be developed independently. Schema, aggregation, and sync contracts should be settled before polishing the count UI.

### Release B: accounts, restoration, and widget

- [ ] Publish the mentor-reviewed verse explanation and contextual teaching.
- [ ] Add Google sign-in, provider linking, and name/photo support.
- [ ] Complete cross-device count-history restoration and visible sync status.
- [ ] Add the goal widget, reusing the same japa route and progress calculations.
- [ ] Verify existing parent/child access and reward behavior remain understandable.

Widget work depends on stable navigation and count state. Google authentication and count synchronization are separate deliverables even if released together.

### Release C: developer messages and dynamic Home content — requested, P1

- [ ] Add a protected admin publishing interface for teachings and encouraging messages, with draft, preview, test-device send, publish, schedule, and unpublish controls.
- [ ] Release a schema-based Home content renderer with local cache, bundled fallback, and independent access to the permanent practice guide.
- [ ] Add announcement notifications through Firebase, with a separate category, opt-in preference, quiet hours, audience selection, expiry, and duplicate protection.
- [ ] Keep publishing to Home and sending a push as separate explicit controls; editing a card must not silently broadcast a new notification.
- [ ] Verify an admin can publish/change a supported teaching on an already installed app without building another APK, and users can read it even if they declined announcement notifications.

Release C is part of the requested scope, not optional polish. Build its content/backend contract alongside Releases A/B if capacity allows; production dispatch follows authenticated admin controls and client compatibility checks. See the [dedicated specification](remote-content-and-notifications.md) for stages C1–C3.

### Release D: optional improvements

- [ ] Per-period personal targets and configurable reminder lead times.
- [ ] Optional preparation checklist using the mentor's examples: dhoti, vibhuti according to one's practice, and water/vessels.
- [ ] Optional silent pacing indicator, periodic haptics, and counter screen-awake preference.
- [ ] Exportable count history and optional private reflection.

Do not prioritize leaderboards, speed competitions, automatic spiritual rankings, or microphone-only rewards for daily japa. They conflict with the requested emphasis on quiet, unhurried practice.

## Decisions to take to the mentor/product owner

These are review items for implementation, not reasons to delay writing this plan. Suggested defaults let engineering prepare concrete designs.

| Decision | Proposed default | Why review is needed |
| --- | --- | --- |
| Are 108 / 32 / 64 mandatory thresholds or tradition-specific guidance? | Display them as the mentor's minimum guidance; always save the actual count, even below the minimum. | Avoid turning a tracking tool into an unsupported judgment about whether Sandhya was valid. |
| Does “Sankalpam goal” mean the short target, Purashcharana, or both? | Credit the same eligible Gayatri event to both active personal goal views, without doubling daily/lifetime totals. | The app has multiple goal concepts and the feedback mentions both. |
| Should manually logged japa satisfy parent-assigned reward goals? | Support personal targets and Purashcharana first; retain the existing family-goal policy until explicitly decided. | Reward completion is a separate policy from private spiritual practice. |
| How should audible japa be presented? | Mental japa is primary. Audible pronunciation learning is clearly labeled; preserve historical accepted counts and the existing explicit counted-audio path during migration. | Decide whether that counted-audio option should remain after the mentor reviews the revised learning flow. |
| How should the six cautions relate to silent japa? | Treat the supplied verse as recitation guidance with context, separate from the recommendation for mental japa. | “Low tone” can appear to contradict Upamsu/Manasika; the interpretation of “as written” also needs care. |
| Does reaching the app's Purashcharana count mean the whole observance is complete? | Say “Your japa count goal is complete”; refer additional observances to the teacher. | The app measures repetitions, not every possible requirement of a tradition. |
| May past entries be added or corrected? | Yes, with the actual practice date, a revision history, and no automatic allocation to a vow that began later. | Catch-up logging and existing-goal attribution must be predictable. |
| Can users change minima or lead time? | Ship mentor minima and a 15-minute reminder default; keep personal targets distinct and make customization a later option. | Avoid expanding the first release unnecessarily. |
| Should the event-time reminder appear after Sandhya is marked observed? | Suppress it for an explicitly observed occurrence; otherwise both stages are enabled by default when the user enables Sandhya reminders. | Avoid a redundant prompt after completion; a count alone does not imply observance. |
| How do we know the guidance was seen? | App-owner decision, 9 September: keep the guide freely available and remove reading-status tracking and acknowledgement. | Display, opening, and acknowledgement are different; none establishes actual understanding. |
| Who can publish remote teachings, and how often? | Explicit developer/editor roles with publisher-controlled sends; optional teaching notifications default to at most one per day with quiet hours. | Roles, editorial review, and frequency are product defaults to confirm, not reasons to block this plan. |

## Scope and review standard

This is a change specification, not a claim that Google sign-in, widgets, new database migrations, reminder fixes, or an admin publishing system have been implemented. No release, remote message, or cloud deployment was performed.

Implementation is ready for release when the mentor can complete the 740-count example offline, reopen without loss, correct an entry, see consistent goal totals, revisit the practice guidance, and receive preparation and event-time reminders without opening the app each day. The full requested scope also includes Google sign-in, cross-device restoration, reviewed teaching content, a functioning widget, developer announcements, and remotely managed Home content.
