# Is the Shuttle Running? architecture

Is the Shuttle Running? (formerly RoutePulse) is a Java 17 / Spring Boot application. The application entry point is
`technology.renatus.routepulse.RoutePulseApplication` and the Maven artifact is
`routepulse`. Spring scans the feature packages beneath the application package.

## Feature packages

All paths below are relative to `src/main/java/technology/renatus/routepulse/`.

| Package | Owns |
| --- | --- |
| `auth/` | Account registration, login, email verification, password reset and account email delivery |
| `organization/` | Organizations, memberships, invitations and checks on acting users |
| `driver/` | Driver records, onboarding and the driver's portal |
| `scheduling/` | Uploaded schedules, import history and demand/availability calculations |
| `assignments/` | Planned coverage, fair recommendations, overrides, outcome reviews and make-up priority |
| `driving/` | Actual driving sessions, NFC/QR checkpoint actions, passenger confirmations and public rider status |
| `reporting/workload/` | Historical record queries and scheduled-versus-actual workload reports |
| `calendar/` | Operating-calendar contract, configured provider, parsed values and saved snapshots |
| `dashboard/` | Dashboard request handling, planning-date data and the template model |
| `marketing/` | Public landing page and demo requests |
| `shared/` | Security configuration, request throttling, shared web advice and exceptions |

Feature subfolders separate `controller`, `service`, `repository`, `model`,
`domain`, `dto` and `email` as needed. JPA entities live in feature `model`
packages. Enums and planning values live in `domain`; workload presentation
values live in `reporting/workload/model`. `DashboardPlan` is a view-data record.

`scheduling/importing` is used because `import` is a Java keyword.

## HTTP and dashboard flow

`DashboardController` resolves the current organization and delegates to
`DashboardPlanningService`. That service selects the planning date, calculates
demand, resolves the calendar and requests assignments only when the existing
calendar gate allows them. `DashboardModelAssembler` builds the template model,
including morning, midday and afternoon coverage attributes.

Separate controllers handle `/upload`, `/park-calendar/confirm-open`, assignment
edits, and manager start/end actions. Existing route paths and request parameters
are retained. Authorization remains in the security configuration and the
organization-aware services.

## Scheduled work and actual driving

`AssignmentPlanningService` owns automatic selection and schedule-window logic.
`DriverAssignmentService` handles manual coverage, driver changes and schedule
review. Its small planning/start/end compatibility methods delegate to the
focused services, preserving existing Java consumers.

`DrivingActionService` owns manager start/end requests; `DrivingSessionService`
owns the actual session lifecycle. `AssignmentReviewService` records reviewed
outcomes and make-up priority. Transactional boundaries and repository locks are
kept on the services that perform these operations.

`DrivingRecordService` loads organization-scoped records in a read transaction.
`WorkloadCalculator` computes the report using those loaded records:

- scheduled minutes belong to `DriverAssignment.scheduledDriver`;
- actual minutes belong to each completed `DrivingSession.driver`;
- multiple sessions for one driver on one assignment count as one shift driven;
- active sessions do not contribute a final completed duration.

`DriverAssignmentRepository` owns assignment queries, including the date-range
query formerly in `DrivingRecordRepository`.

## Invitations

`OrganizationInvitationService` issues and refreshes invitations and sends their
links. `InvitationAccessService` checks who may invite managers or drivers.
`InvitationAcceptanceService` validates a token and creates the invited user and
membership in a transaction. Existing token locks, role checks and email
interfaces are retained. `OrganizationActorService` centralizes membership checks
for users making audited assignment changes.

## Operating calendar

`ParkOperatingDayService` depends on `OperatingCalendarProvider`.
`calendar/sixflags/` contains the current implementation, HTTP client, response
records and parser. It preserves the monthly cache, persisted fallback,
manager-confirmed opening and diagnostic codes.

There is still one configured park/calendar source per application instance,
selected by the existing Six Flags settings. Adding a provider now has a clear
integration boundary; organization-specific provider selection is a future
feature and is not implemented by this cleanup. The existing database park IDs
and snapshot schema remain unchanged.

## Database and configuration

Repositories stay scoped to organizations. JPA entity names, tables, columns,
relationships and versioned Flyway migrations are managed in `src/main/resources/db/migration`. The default
local database name `shuttleflow` remains valid. Database credentials, SMTP
settings, public URLs and existing environment-variable names keep their current
meaning.

## Tests

Tests are grouped under feature packages in `src/test/java/technology/renatus/routepulse/`:

- `migration`: migration/schema initialization;
- `auth`: registration, security and password reset;
- `organization`: invitations and organization isolation;
- `scheduling`: imports, demand and availability;
- `assignments`: automatic/manual/override coverage;
- `driving`: session lifecycle;
- `calendar/sixflags`: adapter response and caching behavior;
- `regression`: cleanup-specific behavior and real Thymeleaf rendering.

`support/MySqlIntegrationTestSupport` shares one MySQL Testcontainers instance for
the test JVM. Spring caches the common application context, and integration tests
roll back their data after each method. The container is intentionally not an
inherited JUnit `@Container`, which would be stopped between feature classes.
Testcontainers manages process cleanup.

The full gate remains `./mvnw clean verify` with Docker available. The existing
GitHub Actions workflow also runs `node --test src/test/js/*.test.cjs` before
that gate and then builds the production image. The driving lifecycle integration
test substitutes a controlled park calendar and covers 11 AM, noon, and 5 PM
openings; it does not depend on an external calendar changing while CI runs.
