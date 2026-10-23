# Is the Shuttle Running?

[![Build and tests](https://github.com/victorpena210/is-the-shuttle-running/actions/workflows/routepulse-ci.yml/badge.svg)](https://github.com/victorpena210/is-the-shuttle-running/actions/workflows/routepulse-ci.yml)

A Java / Spring Boot application that gives riders a shared shuttle-status page,
drivers simple service controls, and managers a record of who drove and when.

**Built and maintained by Victor Peña at [Renatus Technology](https://renatus.technology).**
The project grew from firsthand experience with employee transportation and
wardrobe-counter staffing. It is an independent project; this repository does
not imply employer ownership or endorsement.

[Public website](https://istheshuttlerunning.com) ·
[Project story and engineering decisions](docs/project-story.md) ·
[Architecture](docs/architecture.md) ·
[Local setup](#local-development) · [Tests](#automated-tests)

## See the current application

![Driving history rendered from the current application with fictional records](docs/screenshots/history.jpg)

[View rider and driver screens](docs/screenshots/README.md) ·
[Review the code and decisions](docs/project-story.md#a-short-code-review-path) ·
[Developer portfolio](https://victorpena210.github.io/#shuttle)

These application screens use fixed, fictional sample data rendered through
the Thymeleaf templates.

## The problem and the implementation

Riders need to know whether the shuttle is running. Drivers record actual
service at the shuttle checkpoint, and managers review who drove, when, and
for how long.

| Area | Implemented behavior |
| --- | --- |
| Riders | Public organization-specific pages showing running/not running and the active driver’s first name |
| Drivers | Role-restricted portal, NFC/QR checkpoint actions, start/resume, waiting, break, and end controls |
| Managers | Actual driving history, date/driver filters, driving-time totals, and active-run controls |
| Accounts | Organization-scoped access, invitations, email verification, password reset, and optional persistent sign-in |
| Operations | Versioned MySQL migrations, health probes, optional Sentry reporting, and backup/restore tooling |

**Current session behavior:** starting or resuming service keeps the session
active until an explicit pause, end, handoff, or manager close. Missing phone
location and inactivity do not automatically close sessions. Location sharing
is optional. Temporary manager status overrides still expire at their chosen
time. [Current status behavior](docs/MANUAL-SESSION-STATUS.md).

Managers land on driving history at `/app` (`/driving-records` is an alias).
Live status and run corrections are at `/app/live-status`. Drivers use the
existing NFC/QR checkpoints to start, pause, resume, hand off, and end a run.
The public rider page at `/ride/{slug}` shows whether the shuttle is running
and the active driver’s first name.

## Stack and code tour

- Java 17, Spring Boot 4.1.0, Spring MVC, Spring Security, and Thymeleaf.
- Spring Data JPA, MySQL, and Flyway migrations.
- HTML, CSS, and browser JavaScript; no frontend framework required.
- JUnit, Mockito, MySQL Testcontainers, and Node's built-in test runner.
- Docker packaging and GitHub Actions CI.

Java code is organized by feature under
`src/main/java/technology/renatus/routepulse/`. Start with `driving/` for session
lifecycle and rider status, `organization/` for access boundaries, and
`reporting/workload/` for actual driving history. See [architecture](docs/architecture.md) and
[frontend organization](FRONTEND_ORGANIZATION.md) for the full map.

The former name **RoutePulse** remains in Java packages, artifact names, and
some configuration variables to preserve compatibility. The application entry
point is `technology.renatus.routepulse.RoutePulseApplication`; the built JAR is
`target/routepulse-0.0.1-SNAPSHOT.jar`.

## Local development

Requirements: Java 17 and MySQL 8.4 (or compatible). The executable Maven
wrapper downloads Maven. Docker is required for the integration tests, and
Node.js 22 is used for browser-script tests.

Create a dedicated local database:

```sql
CREATE DATABASE shuttleflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'shuttleflow_admin'@'localhost'
    IDENTIFIED BY 'choose-a-local-password';
GRANT ALL PRIVILEGES ON shuttleflow.* TO 'shuttleflow_admin'@'localhost';
FLUSH PRIVILEGES;
```

From the repository root:

```bash
export DB_PASSWORD='choose-a-local-password'
export SPRING_PROFILES_ACTIVE=dev
./mvnw spring-boot:run
```

Open <http://localhost:8080/register> to create a local organization owner.
Development verification, invitation, and password-reset links appear in the
application console instead of being emailed. Follow the verification link,
then sign in at <http://localhost:8080/login> and create fictional driver records.
Never use real employee exports for a public demonstration.

[.env.example](.env.example) documents configuration names and placeholders;
Spring Boot does not automatically load that file. Set values in your shell,
IDE, container environment, or hosting settings. Do not commit local secrets.

## Automated tests

With Docker running:

```bash
node --test src/test/js/*.test.cjs
./mvnw clean verify
```

Testcontainers starts a temporary MySQL 8.4 instance and applies the migrations
to a blank database. Tests cover authentication, organization isolation,
imports, staffing, session lifecycle, and template behavior. CI runs the same
JavaScript and Maven checks, then builds the production Docker image.

For a focused set of unit/template checks without Docker:

```bash
./mvnw -Dtest=DrivingHistoryTests,PublicRiderStatusTests,SimpleDrivingScreensTests,DriverCheckpointServiceTests test
node --test src/test/js/*.test.cjs
```

Current-workflow tests cover independent driving sessions, stale checkpoint
actions, handoffs, organization isolation, date boundaries, and the minimal
rider page. Retained legacy planning tests also use a controlled calendar.
The CI badge shows the latest workflow result; it is not a claim about the
currently deployed service.

## Production configuration

| Variable | Required | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | Yes | Enables production settings and SMTP delivery |
| `DB_URL` | Yes | Production MySQL JDBC URL |
| `DB_USERNAME` | Yes | Production database user |
| `DB_PASSWORD` | Yes | Production database password |
| `APP_BASE_URL` | Yes | Public HTTPS origin used in emailed links |
| `REMEMBER_ME_KEY` | Yes | Stable private signing key; generate with `openssl rand -hex 32` |
| `REMEMBER_ME_DAYS` | No | Persistent sign-in lifetime; defaults to `90` |
| `MAIL_HOST` | Yes | SMTP hostname |
| `MAIL_PORT` | No | SMTP port; defaults to `587` |
| `MAIL_USERNAME` | Yes | SMTP username |
| `MAIL_PASSWORD` | Yes | SMTP password |
| `MAIL_FROM` | Yes | Verified sender address |
| `APP_REGISTRATION_ENABLED` | No | Defaults to `false` in production |
| `DEMO_REQUEST_RECIPIENT` | No | Business inbox receiving demo requests |
| `SENTRY_DSN` | No | Enables error reporting; blank disables Sentry |
| `SENTRY_ENVIRONMENT` | No | Defaults to `production` in the prod profile |
| `SENTRY_RELEASE` | No | Deployment identifier for error correlation |
| `SENTRY_ERROR_SAMPLE_RATE` | No | Defaults to `1.0` |
| `SENTRY_TRACES_SAMPLE_RATE` | No | Defaults to `0.0` |
| `PORT` | No | Web port; defaults to `8080` |

Set credentials only in the application host's secret/environment settings.
Keep `REMEMBER_ME_KEY` stable across deployments. The old
`RIDER_CONFIRMATION_MINUTES` and `RIDER_OVERRUN_GRACE_MINUTES` settings are ignored.

The uptime workflow defaults to `https://istheshuttlerunning.com` in this
repository. The optional GitHub Actions variable `ROUTEPULSE_PRODUCTION_URL`
overrides that origin. Forks must set their own URL to opt in. GitHub's scheduled
runs may be delayed, so the five-minute cron is not a guaranteed polling SLA.
See [monitoring and recovery](docs/production-monitoring-and-recovery.md).

Before deploying, require passing CI, configure production secrets, verify
`/actuator/health/readiness`, and exercise login, invitations, driver controls,
and rider status. Keep registration restricted to authorized participants.
Verify database backups with an isolated restore drill. This repository does
not establish that a particular host's backups or Sentry alerts are configured.

## Data handling and maintenance

Use fictional people and schedules in examples. Raw employee schedule retention,
registered driver records, and backup retention are separate concerns; see
[privacy cleanup and history handling](PRIVACY_CLEANUP.md) and
[security reporting](SECURITY.md).

Never edit a Flyway migration after it has run against a shared or production
database. Add the next numbered migration instead. Historical migrations V2,
V3, and V10 were sanitized during early development; an old local database may
need the development-only reset described in `PRIVACY_CLEANUP.md`.

Additional references: [NFC setup](docs/NFC-SETUP.md),
[wardrobe hours](docs/wardrobe-opening-hours.md), and
[rebrand history](docs/REBRAND.md). Dated update notes describe earlier changes;
the current code and the session behavior above take precedence.
