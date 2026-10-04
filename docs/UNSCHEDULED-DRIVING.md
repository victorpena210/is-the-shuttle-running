# Unscheduled driving via NFC or QR

## What drivers will see

Keep using the existing NFC tag and QR code. The driver signs in using their linked, active driver account.

- If a scheduled assignment is available, the existing start/resume/coverage workflow applies.
- If no startable assignment is available and nobody is driving, the checkpoint offers **Start unscheduled driving**.
- Confirming records the actual driver, account, UTC start timestamp, organization, and NFC/QR method without creating a scheduled assignment.
- Tap or scan again to pause or end. A handoff closes the previous driver's segment and starts a new segment for the replacement driver.
- After a pause, the next confirmed start creates another segment; previous segments remain in history.

The driver home page shows active unscheduled driving and the latest 20 unscheduled segments. Managers can use **Driving records → Records → Unscheduled driving**, including the existing date and driver filters. Managers can end an active unscheduled segment there if necessary; that action records the manager's account and the MANAGER method.

Completed unscheduled minutes count in actual driving hours, weekly actual-time analytics, and historical workload balancing. They do not create scheduled hours, scheduled shifts, coverage counts, or makeup credits. Open durations are shown as in progress. Date-range reports use the segment's start date in the configured operations time zone.

The public rider page and passenger confirmations support unscheduled service. Existing manual operational-status overrides still apply.

## Install and deploy

1. Copy this project's updated `src` and `docs` files into your existing Git checkout, preserving your current `.git` folder and local configuration. Alternatively apply the included patch from your existing repository root:

   ```bash
   git apply --check /path/to/docs/UNSCHEDULED-DRIVING.patch
   git apply /path/to/docs/UNSCHEDULED-DRIVING.patch
   ```

   Choose either file replacement or patch application, not both. The patch applies to the source version in the ZIP supplied for this change. If `--check` fails, resolve the source-version difference before applying.

2. With Docker running (the project's integration tests use MySQL Testcontainers), run:

   ```bash
   ./mvnw test
   ```

3. After tests pass, commit and push from the project folder:

   ```bash
   git add src docs
   git commit -m "Allow unscheduled NFC and QR driving records"
   git pull --rebase origin main
   git push origin main
   ```

   If the rebase reports conflicts, resolve them before pushing. Do not force-push.

4. Railway deploys the pushed code. Confirm the latest deployment is active and its startup logs report migration **34** successfully applied.

No new environment variables or NFC programming are required. Keep the existing production `REMEMBER_ME_KEY`; the long-login feature is unchanged.

## Database and safety behavior

`V34__allow_unscheduled_driving.sql` makes `driving_sessions.assignment_id` nullable and allows passenger confirmations to reference a driving session without an assignment. Existing records keep their assignment links. No existing migration was edited.

Starts are serialized using the organization row, including when there are no assignment rows to lock. A repeated explicit start cannot turn into a stop or a handoff. Only an explicit **START_UNSCHEDULED** confirmation can create an unscheduled record; a stale scheduled-start form is rejected. GET requests remain read-only, and POST requests retain CSRF protection. Active-account, driver membership, organization checkpoint code, and manager permissions still apply.

The fallback is for unavailable assignments. It does not bypass scheduled-start timing rules, protected breaks, or missing scheduled-time validation for an assignment that is available.

## Verification performed

- Passed executable domain checks for actual driver/account/method audit data, 45-minute duration, pause/end lifecycle, organization isolation, local timestamp presentation, passenger confirmation data, actual totals, no-assignment selection, and scheduled-session regression. These compiled real domain code with temporary annotation-only framework declarations; they do not validate JPA or Spring behavior.
- Parsed all Java sources with the Java 17 parser and checked the patch for whitespace errors.
- Added checkpoint unit tests for explicit unscheduled confirmation, read-only preview, stale actions, and repeated starts.
- Added MySQL integration tests for NFC/QR start/pause/end, stored audit data, CSRF, rendered driver/manager views, public rider status, passenger confirmation cooldown, reporting and organization isolation, handoff, invalid tags, and inactive drivers. Updated the migration test to expect version 34.
- The full Maven build and Spring/MySQL tests could not run in this workspace because Maven Central DNS resolution failed while downloading the Spring Boot parent. Run `./mvnw test` locally before deploying.

## Quick check after deployment

Using an organization with no scheduled assignments and no current drive, sign in on the driver's phone, tap the existing tag, and confirm **Start unscheduled driving**. Check that the driver home says driving is active, then tap again and end it. The two timestamps should appear under **Unscheduled driving** in the manager's driving records. Test records remain in the audit history.
