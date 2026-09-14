# 7. Prioritised Remediation Plan

[← Back to index](README.md)

Reviewed against current source on **2026-09-14**. This replaces the original phase
schedule. Reports 01–06 and their implementation records preserve the original
findings, corrections and verification evidence.

Most engineering actions are already implemented. Remaining work is primarily
release verification, console configuration and product decisions. **Implemented
in source does not mean deployed or verified in production.** Deployment statements
below refer to existing records, not a new live inspection.

## Review of the original actions

Original action numbers are retained for traceability.

| Action | Current status and disposition | Evidence |
|---|---|---|
| 0.1 Backup allowlist | Implemented. Auth preferences and location settings are excluded. Room history and SQLite sidecars remain transferable; modern cloud backup requires encryption capability. The original claim that this stops the database leaving the device is incorrect. | [Android implementation](04-implementation.md) |
| 0.2 Reject weak child PINs | Superseded. Child PIN authentication was removed in favour of Google-only accounts. Do not revive PIN enrolment. | [Authentication implementation](01-implementation.md) |
| 0.3 Bound goal push content | Implemented through fixed notification/data text and explicit error handling. Reward text never enters the cloud push. A new 200-character shared validator would reject progress writes on legacy goals. | [Functions implementation](03-implementation.md) |
| 0.4 Restrict child-code reads | Superseded by deny-all reads/writes on retired `childCodes`. Do not reopen reads to parents. | [Rules implementation](02-implementation.md) |
| 0.5 Non-exported sync service | Implemented; `BIND_JOB_SERVICE` remains. | [Android implementation](04-implementation.md) |
| 0.6 Artifacts and signing key | Debug APK removed, logs archived under ignored `build-logs/`, signing key moved outside the checkout, credentials protected with Windows DPAPI and ACLs. Recovery backup created; downloaded remote copy remains unverified. Release APK deliberately retained. | [Supply-chain implementation](06-implementation.md) |
| 0.7 Console configuration | Verification remains for API restrictions, Auth abuse protection, App Check and privileged roles. Storage was recorded as unprovisioned; deny-all rules are ready in source. | [Authentication](01-implementation.md), [rules](02-implementation.md), [operations](03-implementation.md) |
| 0.8 Strip release logs | Implemented for verbose/debug/info; sensitive warning sites use constant messages without throwable details. | [Android implementation](04-implementation.md) |
| 1.1 App Check | Providers and admin enforcement parameter implemented. Monitoring, real-device validation and service-specific enforcement remain rollout work. Minimum SDK alone does not establish Auth App Check eligibility. | [Authentication implementation](01-implementation.md) |
| 1.2 CI and monitoring | Workflow, emulator tests, secret scan, npm audit and Dependabot configuration exist. Confirm hosted runs and required checks; YAML does not establish branch protection. | [Supply-chain implementation](06-implementation.md) |
| 1.3 Progress validation | Implemented with constrained bootstrap/repair paths and denied client deletion. Preserve bootstrap compatibility. | [Rules implementation](02-implementation.md) |
| 1.4 Self-target/vow validation | Implemented. Vow deletion denied; withdrawal updates `isActive`. Owner self-target deletion intentionally remains because `TARGET_CLEAR` depends on it. | [Rules implementation](02-implementation.md) |
| 1.5 Profiles and private tokens | Implemented with validated profiles, immutable identity fields and owner-private tokens. Recorded migration finished with no legacy public token fields found. | [Rules implementation](02-implementation.md) |
| 1.6 Immutable link codes | Implemented for legacy metadata; new allocation denied. An Admin SDK allocation function would revive a retired feature. | [Authentication](01-implementation.md), [rules](02-implementation.md) |
| 1.7 Account deletion and policy links | Implemented with a freshly authenticated create-only request document, Admin SDK erasure trigger and scheduled retries. Settings exposes deletion and policies. Another callable is unnecessary. Backend deployment recorded; client/document distribution remains separate. | [Privacy review](05-data-protection-and-privacy.md) |
| 1.8 Safe policy errors | Implemented with tagged `PolicyError` and generic unexpected-error responses. | [Functions implementation](03-implementation.md) |
| 2.1 Replace child credentials | Google-only authentication is the recorded owner decision. No numeric sharing handle is issued. Parent provisioning is a separate product decision. | [Authentication implementation](01-implementation.md) |
| 2.2 Replace PIN linking | Implemented with expiring single-use QR invitations and fresh child Google confirmation. | [Authentication implementation](01-implementation.md) |
| 2.3 Children's-data consent | Open product/legal work. Google and QR approval do not establish verified guardianship or parental consent. The privacy review records the owner's decision to keep registration/features available; this plan preserves it. | [Privacy review](05-data-protection-and-privacy.md) |
| 2.4 Retention and encryption | Cloud speech-field redaction after 365 days implemented, with deployment recorded. Local text and transferred history remain. Application-level database encryption and local retention need a separate design. | [Privacy](05-data-protection-and-privacy.md), [Android](04-implementation.md) |
| 2.5 Operational hardening | MFA-gated provisioning tooling and transactional per-admin rate limiting exist. Existing role holders, MFA use, website, deployment and billing alerts require operational verification. | [Functions implementation](03-implementation.md) |

## Remaining release work, in priority order

1. **Coordinate and verify the backend/client release.** Rules/token migration is
   recorded on 13 September; privacy functions/rules/indexes on 14 September. The
   later Functions hardening review reports source changes without deployment.
   Compare the intended release with deployed functions and deploy missing changes
   through the normal release process. Distribute the compatible Android build and
   publish updated privacy/deletion HTML. Older clients cannot use PIN flows, public
   profile tokens or expired speech uploads against hardened rules. Verify deletion
   completion/retries and retention backlog using disposable accounts; preserve
   scores and linked users' accounts.
2. **Complete staged App Check and console verification.** Register the Play
   app-signing certificate, inspect legitimate request metrics and test real Google
   sign-in/QR/Firestore traffic before enforcement. Check Auth integration eligibility
   separately. Configure the external admin website before enabling
   `ENFORCE_ADMIN_APP_CHECK`. Verify API key restrictions, email enumeration protection,
   applicable Auth abuse controls and Storage status. Follow the
   [authentication rollout procedure](01-implementation.md#deployment-and-console-work-still-required).
3. **Verify release behaviour on devices.** Exercise encrypted cloud restore and
   device transfer, recent SQLite writes, same-account profile recovery, excluded
   sessions/location, background jobs and optimized logging. Check Google confirmation
   cancellation/account mismatch, private-token delivery and deletion UI. The
   [device checklist](04-implementation.md#device-verification-still-required) remains
   outstanding; compilation is not device verification.
4. **Confirm CI protection and triage dependencies.** Require the four check names
   in the [activation record](06-implementation.md#github-activation) after successful
   hosted execution. Triage recorded moderate npm findings with a fresh audit and
   compatibility checks. A high/critical-only CI threshold does not mean zero
   vulnerabilities. Preserve the documented signing and recovery process.
5. **Review privileged operations and spending.** Inspect current `messageRole`
   holders, MFA use, IAM and audit retention. The provisioning script guards new
   grants, not existing sessions. Review the separate admin website, configure billing
   alerts and test deployed rate limiting. Public generic teachings remain public;
   rate limiting does not eliminate all request costs. Follow the
   [operations checklist](03-implementation.md#operations-still-required).

Record the project, release revision, date and evidence for each completed item.
Do not mark controls enforced merely because their source exists.

## Remaining design decisions

- **Children's-data policy and consent:** obtain a factual and legal review of
  applicable jurisdictions, controller details, processing location, lawful basis
  and consent requirements. The privacy review qualifies the original legal claims;
  historical audit text is not a current legal determination. Implement an agreed
  consent design without representing Google sign-in, a self-asserted parent role
  or an ordinary email confirmation as proof of guardianship.
- **Local data protection:** decide local speech retention and whether to add
  application-level database encryption. Any migration needs tested key-loss,
  recovery and backup/transfer handling. Cloud redaction does not wipe local copies
  or historical backups.
- **Accepted technical limits:** revisit nested-map validation and document-size
  exposure if profile/progress schemas change; see [rules limits](02-implementation.md#remaining-limits).
  FCM token ownership needs a device challenge protocol if broadcast misregistration
  becomes unacceptable; see the [Functions review](03-implementation.md).

## Invariants to preserve

- No destructive Room migration fallback.
- Preserve QR `getAfter()` validation and atomic single-use semantics.
- Keep practice-history deletion behind Admin SDK erasure. Preserve the intentional
  self-target clearing exception.
- Keep retired `childCodes` reads and writes denied.
- Keep signing keys and plaintext credentials out of the repository.
- Preserve legacy-goal progress and summary bootstrap/repair compatibility.

## Review verification and tracking

This review checked current rules, backup XML, authentication repository, App Check
providers, goal trigger, privacy functions, policy-error handling, admin rate
limiting/provisioning and CI configuration against implementation records. No
additional runtime change was needed for the applicable engineering actions above.
This update changes the plan and index only.

Verification for this review: 24 existing policy/workflow/security-03 tests passed
using handler/provider mocks; relative document links and `git diff --check` passed.
No new Android build, emulator suite or live deployment verification was performed.

The earlier predicted severity totals are withdrawn: they assumed the PIN model
would survive until Phase 2 and that all remaining findings would become low.
Track source implementation, deployment and operational verification separately.
Consent, local data protection and recorded residual risks remain open even after
the release checklist is complete.
