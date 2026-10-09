# Manual session status update — October 9, 2026

## Behavior

Starting or resuming the shuttle keeps service active without repeated phone interaction. A session ends only through an explicit pause, end, driver handoff, or manager close. Existing temporary manager status overrides still apply.

Removed:
- Two-hour confirmation expiry and the scheduled-end confirmation deadline.
- Public status downgrades caused by missing, denied, stale, inaccurate or off-route phone location.
- Ten-minute stationary check-in prompts and twenty-minute inactivity closures.
- Required confirmation controls and countdowns on driver and manager screens.

Optional location sharing remains available inside a collapsed section. Waiting, Driving, On break and End session work without location permission. Waiting persists when the phone stops reporting location. Passenger ride confirmations remain available throughout an active running/waiting session.

A temporary manager override returns to recorded session status when its selected duration expires. Manual manager overrides, driver ownership, organization isolation, authentication, CSRF protection, NFC/QR controls, and historical records remain in place.

## Applying the update

The download contains the updated source project and `manual-session-status.patch`, which includes only this update relative to the exact uploaded project. The schedule screenshot changes already present in your upload are preserved.

1. Extract the ZIP into Downloads.
2. Open Terminal in your existing `RENATUS/is-the-shuttle-running` project folder.
3. Apply the patch using the actual extracted patch path:

```bash
git apply --check ~/Downloads/manual-session-status.patch
git apply ~/Downloads/manual-session-status.patch
bash mvnw test
node --test src/test/js/driver-movement.test.cjs
git diff --stat
```

If the ZIP extracts into a surrounding folder, include that folder in the patch path. If `git apply --check` fails, stop and check whether your local project has newer changes; do not force the patch.

Commit the reviewed changes and deploy through your existing hosting workflow. Rebuild from source; do not reuse an older JAR. No database migration, database reset, or environment-variable update is needed. Existing RIDER_CONFIRMATION_MINUTES and RIDER_OVERRUN_GRACE_MINUTES environment values are ignored by this version.

After deployment, refresh any open driver page once to load the updated controls. Currently open sessions remain open. Sessions already closed by the old inactivity policy remain historical records; start a new session if service is operating again.

The full source copy omits Git metadata and generated build output; keep your existing local repository and configuration.

## Validation performed

- All 250 production Java source files compiled using Java 17 and the runtime dependencies bundled in the uploaded project.
- 35 focused JUnit tests passed, including 61 reliability assertions, session lifecycle checks, authorization, rider status, Thymeleaf rendering and Hibernate mapping/query checks.
- 5 Node browser-script tests passed, including GPS denial, location loss, optional controls and manual pause/end.
- Long-running service, prolonged stationary location, missing location, old persisted prompts, waiting without GPS and expired temporary overrides were covered.
- Full Maven test/build execution was unavailable because the environment could not resolve/download Maven Central dependencies. Live MySQL and real-device deployment tests were not run here.

This update has not been deployed to the live service.
