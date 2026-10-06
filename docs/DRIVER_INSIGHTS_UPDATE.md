# Driver demand and personal analytics

Based on the uploaded project at commit `0877d32` (`organization`).

## Driver experience

The driver navigation now includes:

- **My assignment**: existing schedule and NFC/QR workflow.
- **Today’s demand** (`/driver/demand`): today's aggregate transportation demand for the driver's organization, using the configured operations timezone. Morning arrivals, midday turnover, evening pickups, peak windows, expandable time tables, and off-hours movements use the same calculations as manager demand. The page shows the calendar state and schedule upload time. Missing schedules are explicitly distinguished from zero demand. Drivers can refresh the page for updated demand.
- **My analytics** (`/driver/analytics`): the signed-in driver's recorded driving time, session count, days with driving, optional rider confirmations, unscheduled time, average per day with driving, daily totals, and individual sessions. Defaults to the last 30 days. Today, Monday-through-today, last 30 days, and custom dates are supported, with a 93-day inclusive limit.

Both new pages work without JavaScript and use the existing responsive driver navigation.

## Access and data behavior

Both routes remain under the existing `ROLE_DRIVER` rule. Manager reports, uploads, assignment changes, and calendar overrides retain their existing permissions.

The service resolves the driver and organization from the authenticated account. Neither route accepts a driver or organization selector. Personal repository queries require both IDs, and rider queries return timestamps only. The view receives aggregate demand and personal report models, not employee schedule rows or other drivers' reports. Demand reads do not generate assignments or change driving records.

Analytics attributes actual sessions to the driver who drove, including coverage and unscheduled sessions. Scheduled shifts alone do not add actual driving time. Pauses are excluded. Sessions are clipped to the selected dates and current report time, including sessions that began before the date range. Daily splits use local midnight and elapsed instants, so daylight-saving days can be 23 or 25 hours. Overlaps count once in total driving time. The unscheduled metric is a subset of total time.

Each start/resume is a session. A session spanning midnight counts on both daily rows, but only once in the overall count. Durations round down to minutes, with `<1 min` for positive subminute durations. The average excludes dates without recorded driving time. Rider confirmations are optional submissions attributed to the actual driver, not a complete ridership count. Analytics reflects retained records and is not payroll or attendance scoring.

No database migration or new configuration is required.

## Follow-up: web template rendering fix

The first local Maven package passed all 9 selected tests. After Docker started, the HTTP integration test found that `session` was used as a Thymeleaf loop variable. That name is reserved in web contexts, so the populated analytics page failed to render. The same issue existed in the manager driving-record details.

Both loops now use `drivingSession`. The standalone rendering checks now use a real Thymeleaf `WebContext`, so reserved-variable rules are enforced without needing Docker. The stronger checks reproduced both failures before the fix; all 78 driver insights checks and all 8 cleanup regression groups passed afterward.

If the original driver insights patch is already applied, download and apply only the follow-up patch, with Docker Desktop running:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/driver-insights-template-fix.patch &&
git apply ~/Downloads/driver-insights-template-fix.patch &&
bash mvnw -Dtest=DriverInsightsTests,CleanupRegressionTests,DriverInsightsIntegrationTests clean package
```

The full source ZIP and full driver insights patch also include this fix for fresh installs. Do not reapply the full patch to a project that already has the driver insights feature. The database-backed tests must be rerun locally after applying this fix.

## Apply in the original Git project

Download `driver-insights-update.patch` into Downloads. In Terminal:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running
git status --short
git apply --check ~/Downloads/driver-insights-update.patch
git apply ~/Downloads/driver-insights-update.patch
bash mvnw -Dtest=DriverInsightsTests,CleanupRegressionTests,ServiceReviewTests,PublicDriverNameTests clean package
```

Run each command only if the previous command succeeds. Review existing local changes before applying the patch. If the check reports a conflict or an already-applied patch, stop and inspect it; do not force the patch.

With Docker Desktop running, also execute the database and HTTP authorization tests:

```bash
bash mvnw -Dtest=DriverInsightsIntegrationTests test
```

Sign in as a driver locally and open the new tabs. Test a date range with your recorded driving and a date with no records. Confirm the manager pages are still inaccessible from a driver account.

After reviewing `git diff`, commit the intended changes and push through your usual Git/GitHub workflow. Deploy through your existing process to make the features live. The source ZIP is a full updated copy for reference; the patch is the recommended way to update the existing Git checkout.

## Validation in this workspace

- All 217 production Java source files compiled with Java 17 against the runtime dependencies bundled in the uploaded application JAR.
- 78 driver insights checks passed: time calculations, pauses, overnight clipping, overlapping records, active sessions, optional confirmations, daylight-saving transitions, date range validation, authenticated query scoping, and real Thymeleaf template rendering for populated, empty, missing-schedule, closed-calendar, and error states.
- All 8 existing cleanup regression groups passed, including manager dashboard and Service history rendering.
- The patch was checked and applied to a clean copy of the uploaded baseline, and every changed file was compared with the working result.
- The Maven test run was blocked because Maven Central could not be resolved in this environment. The MySQL/Testcontainers HTTP integration tests are included but were not run here. Run the commands above locally to complete those checks.
