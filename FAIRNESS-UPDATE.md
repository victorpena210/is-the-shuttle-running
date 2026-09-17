# Driver fairness update

This update balances future driving plans using recorded driving minutes plus remaining planned minutes on earlier upcoming dates. Equal-workload drivers rotate using a stable daily order rather than a permanent alphabetical tie-break. After each period is allocated, that duty counts when selecting the next driver. Existing make-up priority remains, but a planned make-up reserves a turn without falsely recording it as completed.

When the normal Driver coverage/dashboard planning page opens, RoutePulse plans all uploaded current/future dates in chronological order. Opening Saturday first therefore uses Friday's allocations too. Future automatic assignments can be redistributed after schedule changes. Read-only live attention polling does not replan.

Coverage eligibility takes precedence over rotation. The planner reserves flexible drivers for later duties: a driver working 9 a.m.–3 p.m. can take morning while drivers working until 9 p.m. cover later demand. It prefers different drivers within a day when available. Different duty lengths, availability, and manager decisions mean total hours are balanced rather than guaranteed to be identical or follow a strict daily round robin.

## Protected records

- Assignment IDs, original suggestions, Git history, and driving history are retained.
- Manual assignments, driver overrides, new manual time edits, manager-reviewed coverage, and reviewed/terminal outcomes are protected.
- Any assignment with driving sessions is protected, including paused and completed sessions.
- Past dates and today's assignments whose scheduled start has arrived are not automatically rewritten. Missing coverage with an already-passed start requires a manager decision.
- Closed or unverified dates do not receive new automatic coverage without the existing persisted OPEN override.
- When no eligible replacement can be planned, existing coverage remains available for the schedule-review workflow.

No database migration or reset is required. Existing legacy AUTO time edits without any stored edit/review marker cannot be distinguished from generated times; use a manager override or recorded schedule review to pin an assignment before replanning.

## Use the update

1. Extract the ZIP into its own folder and open `shuttle-flow` in your IDE. Its original `.git` directory is included.
2. Run the focused tests from that project folder:

   ```bash
   ./mvnw -Dtest=FairnessPlanningTests,DriverScheduleAvailabilityTests,AssignmentWindowPlannerTests,DriverAssignmentPlannedWindowTests,ScheduleReconciliationServiceTests test
   ```

3. Run your normal build/deployment checks. The full integration suite requires Docker/MySQL Testcontainers.
4. Review and commit the changed source/tests and this guide, then push through your normal deployment workflow. Suggested commit message: `Balance planned driver workload and protect assignment overrides`.
5. After deployment, open Driver coverage on an available future date. Eligible future AUTO assignments rebalance automatically. An already-started or manager-pinned assignment will stay as recorded.

This ZIP has not been pushed to GitHub or deployed.

## Validation performed

All five changed production Java classes compiled using Java 17. Sixteen standalone behavior checks passed against the actual planning/domain implementation, with in-memory repository fakes. They cover planned workload, rotation, actual/planned accounting, fill-ins, make-up reservations, chronological planning, unequal duty lengths, repeat-planning stability, protected history, stale assignments, short shifts, calendar gates, and organization isolation.

The local verification used minimal Spring/JPA type stand-ins and the ZIP's compiled support classes; core domain classes were also recompiled from source. It does not validate a running Spring context, JPA persistence/locking, MySQL, or the deployed application. The normal Maven build could not resolve Maven Central in this environment, so the full Maven/JUnit and integration suites remain to be run with normal dependency access. No dependency versions were changed to work around that limitation.
