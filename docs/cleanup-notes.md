# September 9, 2026 cleanup

The input was `shuttle-flow(20260909-145336).zip`, together with the accompanying
backend/templates/tests cleanup notes.

## Completed work

- Grouped Java files by feature and separated persistent entities from planning,
  enum and report values; updated imports and references.
- Replaced `PickupPlanningController` with dashboard, schedule-import,
  assignment, driving-action and park-calendar controllers. Dashboard planning
  and model assembly now have their own classes.
- Extracted automatic assignment planning, manager driving actions, audited
  outcome review and workload calculation from the larger services.
- Moved the two workload result classes out of `DrivingRecordService`.
- Consolidated the date-range assignment query into `DriverAssignmentRepository`
  and removed `DrivingRecordRepository`.
- Split invitation issuance, role checks and acceptance into focused services.
- Introduced `OperatingCalendarProvider`; isolated Six Flags transport/parsing,
  caching and fallback behind that contract.
- Organized screens by audience and split dashboard, coverage and reporting
  into focused Thymeleaf fragments.
- Fixed the extra closing `div` in the original coverage fragment and moved
  the dashboard's coverage visibility condition outside `th:replace` so it is
  evaluated before including the fragment.
- Split the original 11 integration test methods into nine feature test classes
  with a shared fixture; updated the stale migration expectation from 26 to 27.
- Added regression checks for organization membership, workload accounting,
  calendar fallback/cache, dashboard planning gates and real template rendering.
- Renamed the main class to `RoutePulseApplication`, the Maven artifact to
  `routepulse`, and the Spring application name to `routepulse`.
- Updated the architecture and frontend documentation; omitted generated build
  output and IDE metadata from the source archive.

## Verification

| Check | Result |
| --- | --- |
| Production Java compilation | Passed with Java 17 and matching locally available runtime dependency jars |
| Cleanup regression checks | Six groups passed via their standalone Java entry point; also registered as JUnit tests |
| Real Thymeleaf rendering | Dashboard calendar states, empty dashboard, both report views and driver-filtered reports passed |
| Existing availability/demand cases | Eight cases passed using the original test bodies with standalone assertions |
| Endpoint declarations | All 40 checked HTTP mapping declarations match the input |
| JPA entity definitions | All 15 entity bodies match the input after package/import moves |
| Flyway migrations | All 27 are byte-for-byte identical to the input |
| Static assets | All 69 CSS, JavaScript and image files are byte-for-byte identical to the input |
| Template structure | All 49 HTML templates have balanced tags; all 54 fragment references resolve |
| Integration test retention | All 11 original integration test methods are present in the split suite |
| Apply/restore script | Preview, apply, idempotence, restore, conflict protection, stale-build handling and exact Git-file preservation passed |

The full Maven/MySQL integration suite could not be run in this environment:
Maven Central was unavailable through the network, and Docker was not installed.
The available checks do not substitute for that database-backed gate. Run
`./mvnw clean verify` with Docker Desktop running on the development machine, or
use the existing GitHub Actions workflow. No live database or deployed app was
used for verification.

## Applying the source while keeping Git history

The uploaded ZIP did not contain a `.git` directory. The completed bundle includes
`apply-cleanup.py` and `START-HERE.md` outside this project folder. Use that script
to apply the source to the existing checkout. It checks file hashes, backs up
changed files, removes old paths, moves stale build output aside, and preserves
the existing Git directory, branch, remote and index. It does not create commits
or push changes.

The existing local database name and all production environment-variable names
remain valid. Reload Maven and select
`technology.renatus.routepulse.RoutePulseApplication` in the IDE run configuration.
