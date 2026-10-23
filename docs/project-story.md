# Project story and engineering decisions

Is the Shuttle Running? is an independent Java / Spring Boot application by
Victor Peña at Renatus Technology. It records actual driving sessions and
publishes a simple running/not-running status with the active driver's first name.
[Current screens](screenshots/README.md) · [Architecture](architecture.md) ·
[Victor's portfolio](https://victorpena210.github.io/#shuttle)

## Problem and role

The idea came from firsthand employee-shuttle and wardrobe-counter work.
Riders need a quick answer about service; drivers need a small set of actions;
managers need a record of who drove and for how long.

Victor's work spans requirements, the Java application and relational schema,
role-specific interfaces, NFC/QR workflows, tests, deployment configuration,
and ongoing changes. AI tools have assisted development. The source, tests,
and decisions below provide concrete material for technical review.
This is an independent project, not an employer-sponsored product or endorsement.

## Decisions worth reviewing

| Decision | Reason and tradeoff | Evidence |
| --- | --- | --- |
| Record each actual segment | Pausing closes a segment; resuming opens another. Breaks are excluded without rewriting history. | `driving/model/DrivingSession.java`, `driving/service/DrivingSessionService.java` |
| Lock before changing the active run | An organization lock and active-session query serialize competing checkpoint actions. Stale start/end/handoff forms are rejected. | `driver/service/portal/DriverCheckpointService.java`, `DriverCheckpointServiceTests` |
| Enforce organization boundaries | A signed-in user still must belong to the organization and have permission for the action. A tag code does not replace authentication. | `driver/service/portal/DriverPortalAccessService.java`, `OrganizationIsolationIntegrationTests` |
| Calculate time within the selected range | Reports use the operations timezone and clip overlapping sessions to date boundaries. Active runs contribute elapsed time. | `reporting/workload/service/ActualDrivingRecordService.java`, `ActualDrivingRecordServiceTests` |
| Keep public status minimal | The public controller provides only organization name, binary status, and the active driver's first name. | `driving/controller/RiderStatusController.java`, `PublicRiderStatusTests` |
| Keep session endings explicit | Missing phone location is not proof that service stopped. Drivers or managers must end forgotten runs. | `docs/MANUAL-SESSION-STATUS.md`, `src/test/js/driver-movement.test.cjs` |
| Use server-rendered screens | Spring MVC and Thymeleaf keep business rules in Java; focused scripts handle browser interactions. | `templates/`, `static/js/pages/driver-movement.js` |
| Test against MySQL | Testcontainers exercises migrations, mappings, and transactions; Docker is required for the full suite. | `support/MySqlIntegrationTestSupport.java`, `.github/workflows/routepulse-ci.yml` |

Java paths are relative to `src/main/java/technology/renatus/routepulse/`;
tests are under `src/test/java/technology/renatus/routepulse/`.

## A short code review path

1. Follow `/driver/tap` from `DriverPortalController` through `DriverPortalService`
   to `DriverCheckpointService` and `DrivingSessionService`.
2. Inspect the active-driver, stale-request, handoff, and organization checks.
3. Follow `/app` through `DrivingRecordController` to `ActualDrivingRecordService`.
   Compare date-boundary calculations with its tests.
4. Inspect the small public model in `RiderStatusController` and its rendering tests.
5. Run the JavaScript tests and `./mvnw clean verify` with Docker available.

## Boundaries and remaining tradeoffs

- One application and database with organization isolation; not a distributed system.
- Current active service is modeled per organization, not a multi-vehicle fleet.
- Status reflects recorded actions and temporary manager overrides; it is not an ETA.
- A driver who forgets to end a run can leave stale status until a manager corrects it.
- Optional location sharing never automatically closes a session or exposes coordinates to riders.
- Screenshots use fictional fixture data rendered through current templates. They
  demonstrate the interface, not production adoption or a live operational state.
