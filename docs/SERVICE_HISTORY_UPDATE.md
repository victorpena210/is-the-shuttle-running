# Service history and navigation update

This update is based on commit `91c93de` from the October 5, 2026, 6:11 PM project ZIP. It retains the Live shuttle status tab and public first-name formatting from the earlier update. No database migration is required.

## Navigation

- Operations: Overview; Live shuttle status.
- Planning: Schedule imports; Transportation demand; Driver coverage.
- Reports: Service history; Analytics.
- Administration: Drivers; QR & NFC setup; Managers (owner only).

Service history opens `/service-history` on today's date in the operations time zone. Its tabs are Service review, Individual records and Driver summary. Existing `/driving-records` routes and management actions still work.

Service review has Today, Yesterday and This week shortcuts, a custom range of up to 31 days, period and driver selectors, scheduled-versus-recorded driver filtering, and evidence/result filters. Reports clearly label the date, time zone, period scope, planned driver, recorded drivers, calendar state and source of demand. Multiple-day reports display dated periods and totals; there are no unlabeled averages.

The full calendar override controls now appear only in Driver coverage. Transportation demand shows a short calendar notice and a link to coverage. Recorded service has a link to its own review page.

## Review behavior

- Includes scheduled and unscheduled driving and QR confirmations, even without an assignment or available demand data.
- Includes sessions starting before the requested date but continuing into it. Active sessions stop at the report's current time. Overnight assignment scopes stop at the local day boundary.
- Merges overlapping activity intervals so reported service time cannot exceed elapsed time. Period scopes can overlap at demand boundaries; range totals count time and confirmations once.
- Distinguishes upcoming and in-progress periods, missing assignments, unavailable schedules, closed days, unverified calendars, and reviewed outcomes.
- Raises a coverage review flag only for a completed window with configured meaningful demand and no recorded driving or rider evidence, on an operating day with available demand data. Missing optional QR scans alone does not flag a missed service.
- Actual-driver filters limit displayed recordings to that driver. Coverage flags and forecast demand remain service-wide. Scheduled-driver filters select assigned periods and retain evidence from substitutes and unscheduled drivers.
- Keeps recorded activity visible when the park is closed or its calendar is unverified, without inferring a service failure.
- Uses the current effective uploaded schedule when available, then anonymous archived demand totals. Archived period totals retain their original values; precise period-boundary demand timings are not reconstructed or used to flag gaps. Historical calendar status uses persisted snapshots/organization overrides; absent history is labeled unverified.
- Overview service-review alerts use the same new calculations and link directly to the matching date/period.

Demand is employee schedule movement, not confirmed ridership. Rider confirmations count submissions rather than unique people. Recorded service time is elapsed time with at least one recorded driving session, not GPS-verified motion or summed driver labor hours. Unscheduled service time is a subset of total recorded service time. Existing Driver summary retains workload accounting.

## Apply the patch to the original Mac project

Download `service-history-update.patch` into Downloads. Run from your original project:

```sh
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running
git status --short
git apply --check ~/Downloads/service-history-update.patch &&
git apply ~/Downloads/service-history-update.patch
bash mvnw -Dtest=ServiceReviewTests,CleanupRegressionTests,PublicDriverNameTests clean package
```

Review any existing uncommitted work before applying. If the patch check reports a conflict, stop and resolve it against the current files; do not force the patch. Do not apply the earlier shuttle-status patch again.

With Docker available, run `bash mvnw clean verify` for the complete suite, including `ServiceHistoryIntegrationTests`. Review the diff, commit, and push using the existing Git workflow. Deploy the successful build and check `/service-history`, `/app#demand`, `/app#coverage`, and the existing public rider page.

## Validation in the editing environment

- 54 standalone Java checks passed for service evidence, date/driver/result filters, unavailable forecasts, calendar states, overlaps, overnight/active sessions, subminute activity, and daylight-saving transitions.
- All 251 Java files parsed successfully; this is syntax verification, not a full dependency-backed compile.
- Dashboard navigation checks passed using the actual JavaScript and template section attributes.
- HTML tag structure, filter labels, grouped navigation, and calendar-control placement checks passed.
- `git diff --check` passed.
- Added Thymeleaf render checks and a MySQL integration test for overnight unscheduled activity, QR evidence, organization isolation and route permissions. These require the project's Maven dependencies and, for the integration test, Docker.
- Maven compilation and the dependency-backed tests could not execute here because Maven Central DNS/network access was unavailable. Run the commands above locally or in CI before deployment.
