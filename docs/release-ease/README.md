# Play Console release automation plan

Status: Open / deferred. Recorded on 2026-09-14.

This document captures the proposed workflow for later implementation. No release
deployment workflow, Play API access, or CI signing credentials have been configured
as part of this plan.

## Goal

Push a new app version and release notes to the default branch, have CI validate and
build the app, and automatically create a draft release in Play Console. Review and
submission/publishing remain manual.

The repository currently uses `master` as its default branch. Use that branch unless
it is deliberately renamed to `main`.

## Proposed workflow

1. Update the app's `versionCode` and `versionName`, and a release-notes file such as
   `release-notes/en-US.txt`. Additional locales can have their own files.
2. Push or merge the release changes to the default branch.
3. Run the existing security, build, and test checks for that exact commit.
4. If checks pass and the release version has changed, build a signed Android App
   Bundle (`.aab`) using the existing upload key.
5. Upload the bundle for `shastralife.anushtanam` through the Google Play Developer
   API and create a draft release with notes copied from the committed files.
6. Review the draft in Play Console, then submit it for review and publish when ready.

Recommended initial destination: a production draft. Internal testing can be added
as a separate deployment option. Google review and other release requirements still
apply; creating a draft does not make the app publicly available.

Ordinary pushes should run CI without creating a release. The deployment condition
should require a new release version, with a unique increasing `versionCode`.

## One-time setup needed

- Enable the Google Play Developer API in a Google Cloud project.
- Configure service-account authentication and grant only the Play Console app
  permissions needed for the selected release workflow.
- Provide the upload keystore, alias, and passwords through protected CI secrets
  or an external secret manager. Do not commit them or put them in release notes.
- Keep the existing upload key. Its local Windows DPAPI credentials file is tied
  to the Windows account and is not a portable credential source for a Linux runner.
- Use the existing signing environment variables: `ANDROID_KEYSTORE_PATH`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`.
  Place the temporary keystore outside the checkout and set
  `ANDROID_REQUIRE_RELEASE_SIGNING=true` so missing credentials fail the build.
- Choose the final notes format, deployment trigger, and Play track before implementation.

## Implementation requirements

- Deployment must depend on successful checks for the same commit being released.
- Expose signing and Play credentials only to the trusted deployment job, never to
  pull-request validation jobs; clean up temporary signing material after use.
- Validate the release version and reject missing or empty notes before uploading.
- Serialize deployments and handle retries without creating conflicting releases
  or attempting to reuse an already uploaded version code.
- Record the commit, version, target track, and upload outcome in the workflow summary.
- Do not automatically submit or publish production releases in the initial implementation.

## Existing foundation and remaining security work

The existing GitHub Actions CI and automatic dependency submission have passed.
CI currently has no production signing credentials and does not deploy to Play.
Local signing credentials were secured, the upload certificate was matched to Play
Console, and the user confirmed saving the encrypted signing backup to Google Drive.

Remaining work from the security review:

- Triage and fix critical/high Dependabot alerts with compatibility testing. Green
  CI does not mean every dependency alert is resolved.
- Confirm Google API key restrictions and Firebase registration of the Play
  app-signing certificate.
- Decide whether to upgrade the GitHub plan for enforced required checks. The private
  repository's ruleset API rejected access on the plan checked during this session;
  recheck availability when making that decision. CI already runs without enforcement.
- Complete device/release checks before the next public release; current CI does not
  run device instrumentation tests.

See [the security implementation record](../security/06-implementation.md) and
[the app README](../../README.md) for existing controls and local signing/backup usage.

## References

- [Google Play Developer API setup](https://developers.google.com/android-publisher/getting_started)
- [Google Play tracks and draft releases](https://developers.google.com/android-publisher/tracks)
- [Track release fields, including localized release notes](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks)
