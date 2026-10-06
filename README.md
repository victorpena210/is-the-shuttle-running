# Is the Shuttle Running?

Formerly RoutePulse. Public brand: **istheshuttlerunning.com**. See [the rebrand and launch notes](docs/REBRAND.md) before switching the production domain.

The platform converts Dayforce schedule exports into shuttle-demand windows,
driver recommendations, manager-approved coverage, and actual driving records.

## Project organization

Java source is grouped by feature: auth, organization, driver, scheduling,
assignments, driving, reporting, calendar, dashboard, marketing and shared code.

- [Wardrobe opening hours](docs/wardrobe-opening-hours.md): Friday/Saturday setup, counter staffing, and assignment overlap rules.
- [Architecture](docs/architecture.md): backend boundaries, calendar provider and tests.
- [Frontend organization](FRONTEND_ORGANIZATION.md): current CSS, JavaScript and Thymeleaf layout.
- [Cleanup notes](docs/cleanup-notes.md): changes, verification and installation details.
- [Production monitoring & recovery](docs/production-monitoring-and-recovery.md): Sentry, uptime, backups, and restore drills.
- [NFC setup and persistent sign-in](docs/NFC-SETUP.md): tag programming, driver onboarding, and the required production signing key.

The application entry point is
`technology.renatus.routepulse.RoutePulseApplication`. After applying the cleanup,
reload the Maven project in your IDE, update any old run configuration, and run
`./mvnw clean verify` before starting. The packaged artifact is
`target/routepulse-0.0.1-SNAPSHOT.jar`.

## Current capabilities

- organization-isolated users, drivers, schedules, imports, and assignments;
- owner registration, email verification, manager invitations, and password reset;
- versioned Dayforce CSV imports without deleting historical schedules;
- 15-minute pickup-demand windows;
- fair automatic driver recommendations plus manual and override workflows;
- scheduled-versus-actual driving records with the acting manager recorded;
- Six Flags operating-calendar integration with caching, timeouts, and a manual fallback;
- MySQL/Flyway integration tests and GitHub Actions CI;
- production readiness/liveness health probes, Sentry error reporting, and uptime monitoring;

## Requirements

- Java 17
- MySQL 8.4 or compatible
- Docker Desktop when running the Testcontainers integration suite

The Maven wrapper downloads Maven automatically.

## Local development

Create the local database and user if they do not already exist:

```sql
CREATE DATABASE shuttleflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'shuttleflow_admin'@'localhost'
    IDENTIFIED BY 'choose-a-local-password';

GRANT ALL PRIVILEGES ON shuttleflow.*
    TO 'shuttleflow_admin'@'localhost';

FLUSH PRIVILEGES;
```

Set the local password and start the application:

```bash
export DB_PASSWORD='choose-a-local-password'
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

Development verification, invitation, and password-reset links are printed to
the application console. They are deliberately not emailed in the `dev` profile.

Open <http://localhost:8080/login>.

## Automated tests

Start Docker Desktop, then run:

```bash
./mvnw clean verify
```

The tests start a temporary MySQL 8.4 container and run every Flyway migration
against a blank database. GitHub Actions runs the same command and builds the
production Docker image after the tests pass.

For focused unit and template checks without Docker:

```bash
./mvnw -Dtest=CleanupRegressionTests,DriverScheduleAvailabilityTests,TransportationDemandServiceTests,SixFlagsCalendarProviderTests test
```

The full integration suite still requires Docker and MySQL Testcontainers.

## Production environment variables

| Variable | Required | Purpose |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE=prod` | Yes | Enables SMTP delivery and production settings |
| `DB_URL` | Yes | JDBC URL, such as `jdbc:mysql://host:3306/database` |
| `DB_USERNAME` | Yes | Production database user |
| `DB_PASSWORD` | Yes | Production database password |
| `APP_BASE_URL` | Yes | Public HTTPS origin used in emailed links |
| `MAIL_HOST` | Yes | SMTP hostname |
| `MAIL_PORT` | No | SMTP port; defaults to `587` |
| `MAIL_USERNAME` | Yes | SMTP username |
| `MAIL_PASSWORD` | Yes | SMTP password |
| `MAIL_FROM` | Yes | Verified sender address |
| `APP_REGISTRATION_ENABLED` | No | Defaults to `false` in production |
| `SENTRY_DSN` | Recommended | Enables production exception reporting; blank disables Sentry |
| `SENTRY_ENVIRONMENT` | No | Defaults to `production` in the prod profile |
| `SENTRY_RELEASE` | No | Deploy/release identifier for error correlation |
| `SENTRY_ERROR_SAMPLE_RATE` | No | Error sample rate; defaults to `1.0` |
| `SENTRY_TRACES_SAMPLE_RATE` | No | Performance trace rate; defaults to `0.0` |
| `PORT` | No | Web port; defaults to `8080` |

Never commit production credentials or employee schedule exports.

## Production checklist

1. Run `./mvnw clean verify` and confirm GitHub Actions is green.
2. Use a dedicated production database and least-privilege database user.
3. Set every required production environment variable.
4. Confirm `/actuator/health/readiness` returns `UP` over HTTPS.
5. Set `SENTRY_DSN`, generate one controlled test exception in a non-production
   environment, and confirm alert delivery before relying on it operationally.
6. Set the GitHub Actions variable `ROUTEPULSE_PRODUCTION_URL` so the five-minute
   uptime workflow is active.
7. Test registration/invitations, verification resend, password reset, CSV upload,
   manual calendar fallback, driver override, and start/end driving.
8. Keep production registration disabled for the controlled pilot.
9. Enable Railway daily/weekly/monthly database backups and run an isolated restore
   drill before relying on the backup strategy.
10. Limit production access to authorized pilot participants and keep backup files restricted.
11. Keep the employee schedule retention policy and backup retention policy aligned.

## Flyway rule

Never edit a migration after it has been applied to a shared or production database.
Create the next numbered migration instead. Historical migrations V2, V3, and V10
were sanitized during early development; an older local database may need to be
recreated as described in `PRIVACY_CLEANUP.md`.
