# Morning office coverage and optional midday driving

## Behavior

- Only active driver-roster members matched to the organization's uploaded employee IDs count toward morning office staffing. Other employees in the import do not count.
- At least two distinct roster members must be scheduled continuously from 9:00 AM to noon. Duplicate/overlapping rows are counted once per person. Split shifts and staff handoffs are supported when there is no coverage gap.
- The selected driver must also have another roster member available throughout the actual morning driving window, including any buffer before 9 or after noon. Other scheduled driving duties cannot count as office backup.
- One staff member, or a second person arriving at 10, means no automatic morning assignment. The coverage page explains that wardrobe office coverage is required. The app does not shorten the morning block to start when the second person arrives.
- Manual morning creation and edits enforce the same staffing constraint. Update the uploaded schedule if actual availability has changed.
- Midday is optional. A manager sees a suggested driver with specific demand-bearing start/stop windows and an **Assign suggested driver** button. It creates a manual assignment only after acceptance; the server rechecks the date, calendar, availability, fairness and displayed plan before saving.
- Fairness reuses the existing make-up priority, actual driving minutes, reserved planned workload and rotating tie-break. Full demand coverage is preferred when possible. Unaccepted suggestions reserve no workload and never appear as driver assignments or on the public schedule.
- Suggestions avoid overlapping duties and cannot remove the sole office backup during morning driving. Duplicate schedule rows cannot inflate a driver's midday availability.
- Coverage counts and alerts exclude optional unassigned midday and staffing-blocked unassigned mornings. Service review keeps actual evidence visible and does not flag those exempt periods as missing service. Accepted midday assignments remain subject to normal service review.

## Existing records

On the next manager planning-page load, untouched automatic assignments with a future start are withdrawn if they are midday, or if morning staffing is no longer safe. Manual/overridden/reviewed assignments, assignments whose start has passed, and recorded driving history are preserved. Existing morning coverage that conflicts with staffing is shown for review rather than erased. Historical manual record entry remains supported.

There is no database migration or new setting. The 9:00 AM–noon office requirement is explicit in MorningStaffingPolicy. Staffing is derived from the current active roster and uploaded schedule; this does not record attendance, office breaks or call-outs.

## Apply to the original project

This incremental patch starts from the previously delivered driver-insights update **including the template fix**. Apply it once in the Git-connected project, not to a new folder:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/shuttle-staffing-update.patch &&
git apply ~/Downloads/shuttle-staffing-update.patch
```

With Docker Desktop running:

```bash
bash mvnw -Dtest=FairnessPlanningTests,ServiceReviewTests,CleanupRegressionTests,DriverAssignmentIntegrationTests,DrivingSessionIntegrationTests,DriverInsightsTests,DriverInsightsIntegrationTests clean package
```

After BUILD SUCCESS, review and commit:

```bash
git add src docs
git diff --cached --stat
git commit -m "Make midday optional and protect morning wardrobe staffing"
git push
```

Wait for Railway's deployment to succeed, then open Driver coverage for an uploaded future date. Check a one-person morning and a two-person morning; verify that midday starts as an optional suggestion and becomes an assignment only when accepted. If the previous driver-insights work was not yet committed, the same commit will include those staged changes as well.

The accompanying ZIP contains the complete updated source as a backup. Apply the patch to preserve the existing Git checkout.

## Validation performed here

- Compiled all application Java sources with Java 17 and the application's dependency JARs.
- Passed 27 fairness/planning scenario groups, including office staffing, duplicate rows, split coverage, fairness, read-only suggestions, stale and repeated acceptance, and preservation of manager decisions.
- Passed 59 service-review checks and 78 driver-insights checks.
- Passed 8 cleanup regression groups, including actual Thymeleaf web-context rendering of the blocked-morning and optional-midday acceptance states and their coverage alerts.
- Verified the incremental patch applies cleanly to the previously delivered source and reproduces the packaged changes.

The full Maven/MySQL integration suite was not run in this environment. The command above includes updated real-import/database tests and remains the required local check before pushing.
