# Project story and engineering decisions

## Context and role

Victor Peña developed Is the Shuttle Running? through Renatus Technology,
drawing on firsthand employee-shuttle and wardrobe-counter experience. The
work spans the application, scheduling rules, account and organization access,
database schema, public rider page, driver controls, and deployment tooling.
AI tools have assisted development; the code, tests, and documented tradeoffs
are the evidence available for review. No adoption or time-savings figures are
claimed here.

## Decisions worth reviewing

| Decision | Reason and tradeoff | Where to look |
| --- | --- | --- |
| Separate assignments from sessions | A planned shift is not proof of service. Multiple actual sessions can belong to one assignment. | `driving/service/DrivingSessionService.java`, `assignments/model/DriverAssignment.java` |
| Organization-scoped access | Driver and assignment lookups must remain inside the acting user's organization. | `organization/`, `OrganizationIsolationIntegrationTests` |
| Preserve a counter staff member | Opening service must consider who remains available when another roster member drives. | `wardrobe/OpeningCoveragePolicy.java`, `assignments/service/planning/` |
| Explicit session controls | A stopped or disconnected phone is not enough evidence that the shuttle stopped operating. Manual controls trade automatic expiry for operator responsibility. | `docs/MANUAL-SESSION-STATUS.md`, `src/test/js/driver-movement.test.cjs` |
| Server-rendered screens | Spring MVC and Thymeleaf keep most application logic in one Java service; browser scripts handle focused interactions. | `templates/`, `static/js/` |
| Real MySQL in integration tests | Testcontainers exercises Flyway, mappings, and transactions against MySQL, at the cost of requiring Docker. | `support/MySqlIntegrationTestSupport.java` |
| An adapter around the operating calendar | External calendar responses can fail or change; the provider boundary, cache, and manual fallback isolate that dependency. | `calendar/`, `docs/architecture.md` |

Java paths are relative to `src/main/java/technology/renatus/routepulse/`;
test names are under `src/test/java/technology/renatus/routepulse/`.

## Current boundaries

- This is a single application with organization isolation, not a distributed system.
- One configured operating-calendar source is shared by the application instance;
  per-organization provider selection is not implemented.
- Live status records operator actions. It is not a guaranteed GPS location or arrival-time prediction.
- Optional location sharing does not auto-close sessions after inactivity.
- The illustrated walkthrough uses fictional data and is not a production recording.
- Public source does not grant access to production accounts, records, or credentials.

## Review path

Start with the README and architecture notes. Run the JavaScript tests and the
Maven verification suite with Docker. Then follow a manager start/end request
through `DrivingActionService`, `DrivingSessionService`, and the session
repository, and inspect the organization and lifecycle integration tests.
