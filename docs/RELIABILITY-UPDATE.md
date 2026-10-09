> Updated October 9, 2026: confirmation deadlines and inactivity cutoffs below are historical.
> Active sessions now follow manual session actions; see [MANUAL-SESSION-STATUS.md](MANUAL-SESSION-STATUS.md).

# Coverage and rider-status reliability update

## Apply and verify

Use the patch with the project version uploaded on October 6, 2026. From your existing repository:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running
git apply --check ~/Downloads/shuttle-reliability-update.patch &&
git apply ~/Downloads/shuttle-reliability-update.patch &&
bash mvnw clean verify
```

Run Docker Desktop first: the existing integration suite uses a MySQL Testcontainer.
If the patch check fails, stop and use the current project to resolve the difference; do not force a partial apply.
Review `git diff --stat` after verification. Commit and push through the repository's usual deployment process when ready.
The updated ZIP contains the source changes and preserves the original archive's other files. Its bundled `target/` artifacts are from the upload, not a newly built release. Build with `clean verify`; do not deploy an old bundled JAR.

Flyway V37 adds nullable service-confirmation, override-expiration, and organization rider-information fields. Startup applies the migration through the existing Flyway configuration. No destructive data migration is included.

## Manager workflow

- Coverage totals, badges, and attention alerts now use the same demand-interval assessment. A selected driver is not sufficient for a Covered badge.
- Each required demand interval must be covered by usable assignment windows within uploaded work shifts. Breaks, inactive drivers, cancelled/excused/missed assignments, counter staffing, overlapping duties for the same driver, and outstanding schedule decisions affect the result.
- Different-driver assignment windows can jointly cover demand, including overlaps and coverage from another period. This assesses the plan; it does not mean two vehicles are supported simultaneously.
- Required opening demand remains visible when counter staffing prevents assignment. Regular midday suggestions remain optional.
- “Why this assignment?” displays park and wardrobe hours, demand intervals, overlapping uploaded shifts, assigned windows, stored selection reasons, the fairness policy used for automatic selection, and remaining gaps. It does not reconstruct historical candidate scores that were never recorded.
- Under Live shuttle status, confirm an active session after checking that service is running, or close it using its actual end time. A stale form cannot confirm or close a replacement driver's session.
- Owners, admins, and dispatchers can edit public pickup instructions and a help contact in that same panel. These fields start blank; enter the organization's actual details.

## Confirmation policy

The initial defaults are a pilot choice, not proof of the correct cadence for every operation:

| Setting | Default | Behavior |
| --- | --- | --- |
| `RIDER_CONFIRMATION_MINUTES` | 120 | Maximum time since a session start or explicit driver/manager confirmation; clamped to 15–720 minutes |
| `RIDER_OVERRUN_GRACE_MINUTES` | 15 | Confirmation is due no later than the original session window's planned end plus this grace; clamped to 0–120 minutes |
| Temporary manager update | 120 minutes | Manager can choose 15 minutes to 12 hours for the status/message |

Drivers have a “I’m still running the shuttle” action in their portal and should use it only while safely parked. Starting/resuming/handoff creates a new session and supplies new evidence through the existing workflow.

When a session is overdue, the rider page says “Confirmation needed.” It does not silently end the session or alter recorded driving hours. A confirmation at or after the scheduled end can authorize another confirmation interval for legitimate late service. An unscheduled session uses the same maximum confirmation age.

Expired manual overrides do not silently reveal an older open session as Running. They require new service evidence after expiration or a manager's return to Automatic. Returning to Automatic does not renew an old driving session: its freshness must still pass. A still-active manual pause remains in effect when a driver confirms. Pre-update override records expire two hours after their recorded update time.

Browser refreshes and passenger submissions never renew operational confirmation. The public page shows the last operational confirmation separately from its refresh time. Passenger confirmations are rejected server-side while service is unconfirmed. Closing a forgotten session retains an authenticated manager as the closing actor and uses the actual end time entered, so the current time is not automatically counted as driving time.

## Rider layout

Status and confirmation time → next remaining planned window → pickup/help → full schedule → ride confirmation.
Cancelled/missed windows are not selected as the next service window. Organization text is rendered as escaped plain text. Driver names continue using the existing first-name-only public display.

## Validation and limits

The entire production source compiled with Java 17 against the application's bundled dependencies. The new executable reliability suite and existing planning, wardrobe, driver, reporting, and real Thymeleaf rendering checks passed. The JUnit wrapper runs the reliability checks with Maven as well. The migration test now expects V37.

The full Maven/MySQL integration suite could not run in the editing environment because Maven Central DNS/network access was unavailable. Run `bash mvnw clean verify` locally before deployment. Actual-device usability and complete operating-day checks remain pilot work; use `docs/RELIABILITY-PILOT.md`.

This update does not add multi-organization switching, organization-specific calendar/time-zone providers, or multiple concurrent vehicle sessions. The review proposed using a second pilot to guide those larger changes.
