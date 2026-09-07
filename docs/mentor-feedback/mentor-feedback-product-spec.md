# Mentor feedback: product requirements

Status: proposed; updated 7 September 2026. Parent document: [change plan](mentor-feedback-plan.md). Engineering details: [implementation plan](mentor-feedback-implementation-plan.md). New publishing scope: [remote notifications and Home content](remote-content-and-notifications.md).

The requested religious guidance comes from the mentor's supplied message. Copy marked “draft” needs editorial review before publication. Recommendations extending that message are explicitly identified.

## 1. Sandhyavandhanam Gayatri counting — MF-01

### 1.1 Today's Sandhya cards

Each period should show its name, local date, solar-event time, preparation and event-time reminder settings, observance state, Gayatri count, and minimum guidance:

| Period | Mentor's minimum | Example card |
| --- | ---: | --- |
| Pratah / Prātaḥ | 108 | “Gayatri: 108 · Minimum guidance: 108” |
| Madhyahnika / Mādhyāhnika | 32 | “Gayatri: 32 · Minimum guidance: 32” |
| Sayam / Sāyam | 64 | “Gayatri: 600 · Minimum guidance: 64” |

The combined minimum guidance is **204 per day**. This is a reference total, never a quantity automatically credited by the app. Use one consistent user-facing spelling across screens; existing enum `MADHYAHNA` can remain an internal compatibility identifier.

- [ ] Show “Count not recorded” when quantity is unknown. Do not turn an old “observed” record into zero or the minimum.
- [ ] Provide “Start mental japa,” “Record count,” and an independent “Mark Sandhya observed” action.
- [ ] After logging, provide “Add more” and “Edit count/history.” The card rolls up all contributions for that date and period.
- [ ] Allow entry without enabling reminders, granting location permission, or waiting for a reminder to fire.
- [ ] Keep the practice date/period visible while entering or correcting a count; default to today and the selected card.
- [ ] Never assume reaching 108/32/64 means the entire Sandhyavandhanam ritual was performed.

### 1.2 Count-entry sheet

Use a large, readable numeric total with a roll-up wheel or stepper and a numeric-keyboard alternative. A wheel alone is too slow for 600 and can be difficult with assistive technology.

- [ ] Support decrement/increment, optional +10/+108 shortcuts, and direct entry of any valid whole-number count.
- [ ] Offer 108, 32, and 64 as deliberate presets appropriate to the period. A preset only changes the draft; saving remains explicit.
- [ ] Do not cap Sayam at 64, 108, or 600. Hundreds and larger valid counts must be supported without one database record per repetition.
- [ ] Label the operation clearly: **“Add more” adds a quantity; “Edit total” sets an absolute total.** Never interpret an edited 600 as an extra 600.
- [ ] If a live session already contributed 100, show that in the sheet. Recording a final total of 600 must produce 600, not 700.
- [ ] Show a save summary: practice date/period, quantity being added or corrected, new period total, and affected goals.
- [ ] Reject negative counts, decimals, invalid text, arithmetic overflow, and future practice dates. A positive quantity creates a japa entry; zero may be recorded explicitly as an observance-only state, or used to remove an existing manual contribution.
- [ ] Save an actual count below the guidance with neutral wording such as “Recorded 40 · Minimum guidance: 64.” Do not refuse it or label the person a failure.
- [ ] Disable duplicate submission while saving and make repeated delivery of the same save operation harmless.
- [ ] After saving, show “600 Gayatri recorded for Sayam” and offer correction/undo. If offline, show that it is saved on this phone and awaiting sync where applicable.

### 1.3 Live mental-japa counter

- [ ] Provide a large +1 touch target, undo last increment, optional mala-sized increments, pause/resume, and finish.
- [ ] Keep sound off by default. Haptics are optional and should not fire on every count unless chosen.
- [ ] Persist the draft count, profile, mantra, practice date, and period through rotation, backgrounding, and process recreation.
- [ ] On recovery, offer “Resume,” “Save completed count,” and “Discard draft.” A recovered draft has not yet been credited to goals.
- [ ] Save a completed session once. If the user saves partial progress and continues, only the new unsaved quantity is credited next time.
- [ ] Do not infer completed repetitions from elapsed time, reference-audio loops, or screen taps unrelated to the counter.
- [ ] Keep the date attached to an ongoing session stable across midnight; explain it on finish and allow an intentional correction.
- [ ] Display progress after reaching a target while allowing the user to keep counting. A 64 minimum must not end a 600-repetition evening.

### 1.4 Acceptance examples

| Scenario | Expected result |
| --- | --- |
| Record 108, 32, and 600 for the three periods | Day total is 740; the period cards show the original quantities. |
| Mark all three observed without entering counts | Observance is recorded; no japa is invented. |
| Save 64 for Sayam, then use “Add more” for 536 | Sayam total is 600. |
| Change Sayam's final total from 600 to 500 | Day total changes from 740 to 640; applicable goal projections are recomputed, respecting goal caps. |
| Tap Save twice or retry after a connection failure | Only one contribution is applied. |
| Finish a 100-count live session, then set the final period total to 600 | The new addition is 500, with a final total of 600. |
| Leave a 57-count draft and the process is killed | The draft can be recovered at 57; it has not also been silently credited. |

## 2. Sankalpam and Purashcharana accounting — MF-02

### 2.1 Eligibility and totals

- [ ] One completed Gayatri repetition has a numerical weight of one, regardless of chanting method. Spiritual descriptions must never become count multipliers.
- [ ] Attribute every contribution to its owner/profile and mantra. Gayatri japa must not advance a different mantra's goal.
- [ ] Apply eligible Sandhya and standalone mental-japa contributions to the personal target and Purashcharana applicable to that practice date. These are two projections of one event, not two events.
- [ ] Keep goals explicitly linked by stable identity; never redirect an old correction to whichever target happens to be active now.
- [ ] If there is no eligible goal, retain the japa in daily/lifetime history. Saving a count must not force enrollment in Purashcharana.
- [ ] Proposed default: a goal begins accumulating at enrollment; earlier japa is not automatically imported. A backdated entry may count toward the identified goal that was in effect at the time.
- [ ] Keep `PRACTICE` learning sessions outside goal counts. Preserve historical accepted `NORMAL` recitations. Any retained counted-audio route must use the same accounting path as other eligible entries.
- [ ] Keep self-reported count history distinct from audio assessment results. Manual/mental entries have no pronunciation score, transcript, or “verified by microphone” claim.
- [ ] Proposed default: do not change parent-assigned reward eligibility for manual counts until that family policy is reviewed. Explain eligibility where it matters; never silently credit a reward.

### 2.2 Goal caps, edits, and milestones

- [ ] Preserve all performed repetitions in history. Cap a goal's displayed credited progress at its target and its remaining count at zero.
- [ ] Example: a target has 100 remaining and the user saves 600. Record 600 performed, show 100 newly credited to that target, and keep the additional 500 in japa history. An eligible larger Purashcharana can receive all 600.
- [ ] Do not automatically create a new goal or move excess repetitions into a later vow.
- [ ] Detect every milestone crossed by a bulk addition, even when the new total does not equal a milestone exactly. Show a restrained, silent summary.
- [ ] Corrections must reduce totals as well as increase them. If a correction drops a goal below its target, recompute the count-based completion state and explain the correction.
- [ ] Keep a goal's lifecycle separate from its count-based completion. Correcting an older goal must not replace a newer active goal or create two active personal targets.
- [ ] Keep withdrawn vows withdrawn; correcting their historical entries must not reactivate them.
- [ ] Suppress repeated celebrations on sync replay or repeated rendering. Preserve audit history when a correction changes a previously reached milestone.
- [ ] Replace “counts cannot be entered by hand” and “only recitations heard and accepted by the app count” in enrollment/help copy.
- [ ] Recommended completion wording: “Your japa count goal is complete. Follow your teacher's guidance for any further observances.” Have the mentor review the current broader Purashcharana completion wording.

### 2.3 Acceptance criteria

- [ ] The 740-count example adds 740 to an eligible goal with sufficient remaining capacity; total performed japa remains 740 across all views.
- [ ] Practice scores and word-mastery averages do not change when a user logs 600 mental repetitions.
- [ ] Editing 600 to 500 works locally and after sync; an older cloud snapshot cannot restore 600.
- [ ] Independent entries of 108 and 600 on two offline devices combine to 708 after sync. Concurrent edits of the same entry are surfaced or resolved by an explicit revision policy, never silently summed as new practice.
- [ ] A repeated record from an app restore is deduplicated; an unrelated new session is not discarded merely because its count matches an older session.

## 3. Teaching and session entry — MF-03, MF-04, MF-05

### 3.1 Show the methods before japa and keep them visible in the app

Use a short “Before you begin” panel before a first-time practitioner starts from Home, Sandhya, the Japa tab, a reminder, or the widget. MF-03 also requires permanent placement after onboarding: a Home “Gayatri practice guide” card and a compact, visible teaching reminder in Japa and Sandhya. A Settings/Help link alone is insufficient. Keep the full comparison one tap away through “How to practise.”

- [ ] The permanent Home guide shows a short message such as “Gayatri japa: repeat mentally at a steady pace” with “Learn the three methods.” It remains available regardless of the rotating or remotely published teaching.
- [ ] The Japa and Sandhya pages show a compact reminder near the start action. Users can collapse the longer explanation, but the guide entry remains visible.
- [ ] Store `guidanceVersion`, `openedAt`, and `acknowledgedAt` per profile; acknowledgement requires an explicit “I have read this” action. Opening or auto-displaying the panel must not set acknowledgement.
- [ ] If dismissed without acknowledgement, show a gentle “Review the practice guide” prompt on Home and near the next Japa start. Do not block counting or repeatedly open an intrusive modal.
- [ ] Resurface materially revised guidance for existing users and make it available offline. An optional sync of acknowledgement can carry it to another phone, but the guide is visible on every phone regardless.
- [ ] Do not claim that an impression or acknowledgement proves understanding. Developer reporting of individual acknowledgements is not required; any later aggregate reporting must distinguish shown, opened, and acknowledged.

Draft primary message:

> For daily Gayatri japa, our mentor recommends repeating mentally, without chanting aloud. Keep the steady pace demonstrated in the reference audio. Audible repetition can be used when learning pronunciation.

Draft comparison, based on the mentor's message:

| Method | Plain-language description | How the app should support it |
| --- | --- | --- |
| Vaikhari | Audible repetition using the voice; others nearby can hear it. The mentor describes it as useful while learning. | An explicit pronunciation-practice choice, with microphone consent only when needed. |
| Upamsu | Very soft repetition, with lip/tongue movement, heard only by the practitioner. | Manual counting; no expectation that speech recognition can hear or score it. |
| Manasika | Mental repetition, without movement of lips, tongue, or throat. | Default daily-japa mode; silent manual counter and later count entry. |

The mentor describes a progression from external audible practice toward internal mental repetition and regards mental japa as the most effective. Attribute that teaching to the mentor/tradition. Avoid unsupported quantified claims about spiritual energy or efficacy. The supplied Upamsu wording mixes “whispering/humming” and “no distinct sound”; have the mentor approve the final wording.

- [ ] Make “Start mental japa” the primary action and “Practise pronunciation” a separate learning action.
- [ ] Mental japa must work with microphone permission denied, speech services unavailable, and the Hindi voice model absent.
- [ ] Never open the microphone on launch from a notification or widget.
- [ ] Keep reference audio available on deliberate request. Playing or looping it does not add japa.
- [ ] Demonstrate the reference pace at normal speed; do not encourage faster completion. Any slower learning aid must be identified as a learning aid, not the recommended daily-japa pace.
- [ ] Put learning scores in the learning experience; avoid grading a silent session as incorrect because no speech was heard.

Acceptance: a user dismisses onboarding, later finds the permanent Home card and Japa/Sandhya reminder, opens the full three-method guide offline, and acknowledges the current version. The reminder to review clears while the permanent guide stays available. A new remote Home teaching must not hide or replace it.

### 3.2 Supplied verse and the six cautions

Preserve the following mentor-supplied text in the teaching resource, with readable line breaks:

> गीती शीघ्री शिरःकम्पी यथालिखितपाठकः ।  
> अनर्थज्ञोऽल्पकण्ठश्च षडेते पाठकाधमाः ॥

> gītī śīghrī śiraḥkampī yathālikhitapāṭhakaḥ ।  
> anarthajño'lpakaṇṭhaśca ṣaḍete pāṭhakādhamāḥ ॥

Attribution supplied by the mentor: **पाणिनीयशिक्षा — Pāṇinīyaśikṣā**. No edition or verse number has been independently verified for this plan.

| Term | Meaning supplied in the feedback | Editorial treatment |
| --- | --- | --- |
| Gītī | Reading like a song | Explain the mentor's caution against turning the recitation into a song; do not equate prescribed Vedic accent with arbitrary melody. |
| Śīghrī | Reading in a hurry | Encourage steady pace; remove speed-based motivational copy. |
| Śiraḥkampī | Shaking the head | Present a gentle practice instruction, not a posture-detection feature. |
| Yathālikhitapāṭhakaḥ | Reading just as written | Explain the mentor's emphasis on hearing and learning the tradition; review whether the intended caution is mechanical text-only learning. Do not remove written learning aids based on an unresolved translation. |
| Anarthajñaḥ | Reading without knowing the meaning | Link to mentor-reviewed meaning and pronunciation resources. |
| Alpakaṇṭhaḥ | Reading in a low tone | Review the context of vocal recitation and distinguish it from mental japa; do not turn this into “chant louder.” |

- [ ] Explain that the mentor uses Uttamam, Madhyamam, and Adhamam as quality descriptions; do not attach “bad reader” labels to app users or create a spiritual ranking system.
- [ ] Resolve the apparent low-tone/mental-japa conflict in mentor-approved explanatory copy before publishing the verse as instructions for japa.
- [ ] Include all six items. The earlier prose paraphrase names four, but the later supplied verse and meaning contain six.
- [ ] Make this guidance directly accessible before practice and from Help; a randomly rotating Home teaching is insufficient for essential instructions.
- [ ] Review Devanagari, IAST diacritics, pronunciation, text wrapping, attribution, and any explanation of Shruti together with the mentor.

## 4. Sandhya reminder setup and two reminder times — MF-06

### 4.1 Timing and navigation

- [ ] For each enabled period, schedule **two reminders**: preparation at **15 minutes before** and another **at the Sandhya event time**. With all three periods enabled, this is up to six reminders per day.
- [ ] Show both reminder stages, for example: “Sayam at 6:20 PM · Prepare at 6:05 PM · Sandhya reminder at 6:20 PM.” The settings, preview, scheduler, and notifications must agree.
- [ ] Inside Sandhya, provide separate “15 minutes before” and “At Sandhya time” switches. Both default on when enabling reminders; users can turn either off.
- [ ] Preserve the user's master reminder-enabled choice on upgrade; replace the old implicit 5-minute default with 15 minutes. Explain the added event-time reminder and expose its switch; never turn a disabled master setting on.
- [ ] Draft preparation notification: “Sayam is at 6:20 PM. Allow time to prepare for your Sandhya.” Draft event notification: “It is time for Sayam Sandhya.” If delayed, show the actual event time rather than falsely saying 15 minutes remain or “now.”
- [ ] Notification tap opens the matching date and period. Offer “Open Sandhya”/“Record count”; retaining “Mark observed” must not add 64/108/32 automatically.
- [ ] Preserve account/profile ownership if an old notification is tapped after account switching. Do not record against the newly active profile by accident.
- [ ] If enabled inside the 15-minute lead window, retain today's event-time reminder. Offer one immediate preparation cue when useful; proposed default: skip that extra cue if the event is within two minutes, to avoid back-to-back alerts.
- [ ] If a delayed preparation cue arrives at the same time as the event reminder, coalesce to one event-time notification. Retries must not duplicate either stage.
- [ ] Proposed default: suppress remaining reminders for an occurrence explicitly marked observed. Logging japa alone does not suppress them. Dismissing the preparation notification must not cancel the event-time reminder.
- [ ] Both stages refer to the same Sandhya occurrence; they must not create duplicate observance records or count toward japa.
- [ ] Recompute after location/timezone/clock changes and reboot. Add package-update recovery where needed. Do not notify old dates after long delivery delays.

### 4.2 “Set up reminders” panel inside Sandhyavandhanam

Add a visible “Set up reminders” / “Improve reminder delivery” card in Sandhya, beside the reminder controls. It explains and opens the relevant Android prompts/settings from that screen and rechecks state when the user returns.

| Setup item | Action and status |
| --- | --- |
| Notifications | Explain preparation/event reminders, then request notification permission on the user's action. If denied, show “Not allowed” and a route to app notification settings. |
| Sandhya notification channel | Show allowed/blocked and open that channel's settings for sound/vibration and visibility. Enabling the app preference cannot override a blocked Android channel. |
| Reminder precision | If the selected implementation qualifies for and uses exact alarms, offer “Allow precise reminders” and open Alarms & reminders special access. Otherwise show the approximate-delivery mode; never display a grant button for unsupported access. |
| Battery/background settings | Offer optional “Check battery settings” troubleshooting if delivery is restricted. Open available app/system settings, with clear return instructions when a device-specific shortcut is unavailable. |
| Test and upcoming times | “Send test notification,” plus the next preparation and event-time reminder for each enabled period. Distinguish a posting attempt from the user's confirmation that they saw it. |

- [ ] Clearly show “Ready,” “Notifications blocked,” or “Approximate timing,” with the concrete action needed. A ready state means checks passed, not guaranteed delivery.
- [ ] Recheck permission and channel state after returning from Android settings and after revocation; rearm eligible reminders without duplicating them.
- [ ] Keep count tracking usable if permissions are declined. Do not ask for microphone, contacts, background location, or unrelated permissions in reminder setup.
- [ ] Enabling Sandhya notifications must not opt the user into developer teaching/announcement notifications; those have a separate preference and channel.

Android exposes battery-optimization settings; direct exemption requests have narrower acceptable uses. Make this a targeted settings aid, not an unconditional demand for exemption. [Android Doze and App Standby guidance](https://developer.android.com/training/monitoring-device-state/doze-standby).

### 4.3 Reliability and diagnostics

The existing scheduler requests inexact alarms. Android may defer delivery; scheduling for 15 minutes before an event is not a guarantee of an exact arrival time. Reuse the background alarm mechanism and measure actual delivery before deciding whether another precision strategy is needed. Exact alarms have additional access requirements; do not assume the app qualifies for a particular permission merely from its feature name. [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms/schedule).

- [ ] Provide reminder status: enabled periods/stages, selected location/timezone, both next scheduled times, and app/channel notification availability.
- [ ] Offer a user-triggered test notification and a link to the relevant notification settings when blocked.
- [ ] Request notification permission in context when enabling reminders. Android 13+ requires runtime notification permission for this flow. [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- [ ] Keep debugging data for scheduled time, receiver time, posting attempt/result, and rearm result, without logging unnecessary personal data.
- [ ] Test while the app is backgrounded, dismissed from recents, the screen is off, the process is no longer running, and the device is idle or offline. Test reboot and multiple consecutive days without reopening.
- [ ] Treat an explicit system force-stop separately from ordinary app closure. Document platform restrictions and recovery; do not promise to bypass a force-stop.
- [ ] Do not require internet or repeated live location access once a location is saved.

Pass criterion: reproduce and explain the mentor's reported failure on their phone or an equivalent configuration, then demonstrate the setup flow, a preparation reminder at the requested -15-minute offset, an event-time reminder, and the next day's rearming without reopening the app. Record actual timing and supported conditions; emulator-only success does not close this defect. Test denied/revoked permissions, one stage disabled, observed-before-event suppression, late enablement, and delayed-stage coalescing.

## 5. Google sign-in, profile, and recovery — MF-07, MF-08

- [ ] Add a recognizable “Continue with Google” action to the adult sign-in flow; preserve email/password and the established child ID/PIN flow.
- [ ] Import display name and available profile photo; support a missing photo, a failed image load, and an offline fallback avatar. Do not overwrite a deliberate local name/photo preference silently.
- [ ] Keep identity and local profile mapping stable. Linking Google to an existing account should retain its progress and family links, rather than create a second history.
- [ ] Handle cancellation, no eligible account, unavailable services, offline attempts, expired credentials, and credential/account collisions with clear recoverable states.
- [ ] Keep account choice separate from family role. Google identity alone does not decide that a user is a parent or grant access to a child's data.
- [ ] For a previously local-only profile, show which account will receive its history and use a one-time, idempotent association/import flow.
- [ ] Sign-out and account switching must isolate local drafts, queued uploads, and widgets. Pending work remains associated with its original owner.
- [ ] Show “Saved on this phone,” “Waiting to sync,” “Synced,” or “Sync needs attention” where appropriate, with last successful sync information.
- [ ] Define the backup promise explicitly: new japa entries, corrections, Sandhya observance/count history, and applicable goal state are restored; existing detailed speech analytics/audio are not automatically included.
- [ ] Provide a recoverable state if sign-in succeeds but profile loading or synchronization fails.
- [ ] Update privacy/account-deletion descriptions to match any new stored photo references and count history.

Implementation recommendation: use Android Credential Manager with Firebase's Google authentication flow. Link credentials to an existing authenticated Firebase account to preserve its UID where applicable. These APIs provide authentication; count-history synchronization still requires the application work specified here. [Firebase Google sign-in](https://firebase.google.com/docs/auth/android/google-signin), [Firebase account linking](https://firebase.google.com/docs/auth/android/account-linking).

Acceptance: an existing email user links Google and sees the same count; a returning Google user restores newly supported count history on another phone; a cancelled sign-in leaves the previous session intact; account B never receives account A's pending counts.

## 6. Android goal widget — MF-09

### 6.1 First version

- [ ] Provide a resizable home-screen widget with a compact layout and a larger layout.
- [ ] Display selected profile label, mantra name, goal label, completed/target count, remaining count, and a “Start japa” action.
- [ ] Configuration chooses a personal target or Purashcharana. If only one is available, preselect it; if neither exists, offer “Set a goal” while still permitting japa without a goal.
- [ ] Tapping Start opens the matching mantra's mental-japa screen directly, with initial guidance if needed. An explicit widget tap may begin a silent counter draft; it must never begin audio playback or microphone capture automatically.
- [ ] If a draft exists, resume that draft instead of creating duplicates. If the selected goal is finished or removed, show that state and offer goal selection.
- [ ] Refresh from local state after save/correction, target changes, sync, sign-in/sign-out, and day changes. It must be useful offline and must not require cloud access for rendering.
- [ ] Provide safe empty, loading, stale, signed-out, and unavailable-profile states. Prevent an old widget from opening another account's data.
- [ ] Allow a privacy option to hide the name or detailed total; avoid making the profile photo mandatory on the home screen.
- [ ] Support large text, high contrast, TalkBack labels, light/dark appearance, and common launcher sizes.

Jetpack Glance is a candidate implementation because the app uses Compose, but widgets require their own supported UI components; existing screen composables cannot simply be reused unchanged. [Android Glance documentation](https://developer.android.com/develop/ui/compose/glance).

Acceptance: widget count agrees with the app after recording and editing Sayam; one tap reaches the intended mantra; repeat taps do not duplicate drafts; it works offline; account switching clears or invalidates the previous owner's display.

### 6.2 Optional later improvements

Show the next Sandhya reminder in the expanded widget, allow a deliberate “Record count” shortcut, and add a private progress-only layout. Keep direct count mutation inside the app for the first release to reduce accidental home-screen increments.

## 7. Shared quality requirements

- [ ] Use the existing app theme and string resources; test Devanagari and diacritics at large font sizes.
- [ ] Provide at least 48 dp interactive targets and an accessible alternative to wheel gestures.
- [ ] Separate ritual observance, total japa, and pronunciation learning statistics on Progress. Do not treat unrecorded counts as zero effort.
- [ ] Keep count saves responsive and local, even with hundreds of repetitions or no network.
- [ ] Avoid repeated prompts, audible celebrations, speed rewards, and full-screen interruptions during mental japa.
- [ ] Update Home, Sandhya, Japa, Purashcharana, Progress, Settings, notifications, Help, debug review fixtures, and relevant screenshots together so terminology and behavior remain consistent.

## 8. Developer announcements and dynamic Home content — MF-10, MF-11

This is an additional requested P1 release stage. The full product/admin flow, architecture, and acceptance tests are in [Remote notifications and Home content](remote-content-and-notifications.md).

- [ ] Authorized developers can draft, preview, test-send, send/schedule, cancel, and inspect a teaching/message campaign.
- [ ] Users can independently enable “Teachings and messages,” set quiet hours, and read published Home content with that notification preference off.
- [ ] Home has a remotely managed teaching/message area with a full-content destination, cached content, bundled fallback, and expiry handling. The permanent MF-03 practice guide remains visible separately.
- [ ] Developers can publish, reorder/pin, schedule, edit, unpublish, and restore a supported content revision without another application release.
- [ ] A Home publish or edit does not automatically send a push; sending is a separate intentional admin action.
- [ ] Notification taps open the matching content, including from a cold launch. Old, expired, unpublished, or incompatible content has a clear fallback.
- [ ] After the initial supporting app release, verify both a remote Home-content update and a test campaign on an already installed compatible app without updating its APK.
