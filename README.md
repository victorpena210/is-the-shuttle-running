# RoutePulse

RoutePulse converts Dayforce schedule exports into shuttle-demand windows,
driver recommendations, manager-approved coverage, and actual driving records.

## Current capabilities

- organization-isolated users, drivers, schedules, imports, and assignments;
- owner registration, email verification, manager invitations, and password reset;
- versioned Dayforce CSV imports without deleting historical schedules;
- 15-minute pickup-demand windows;
- fair automatic driver recommendations plus manual and override workflows;
- scheduled-versus-actual driving records with the acting manager recorded;
- Six Flags operating-calendar integration with caching, timeouts, and a manual fallback;
- MySQL/Flyway integration tests and GitHub Actions CI;
- production health endpoint at `/actuator/health`.

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
| `PORT` | No | Web port; defaults to `8080` |

Never commit production credentials or employee schedule exports.

## Production checklist

1. Run `./mvnw clean verify` and confirm GitHub Actions is green.
2. Use a dedicated production database and least-privilege database user.
3. Set every required production environment variable.
4. Confirm `/actuator/health` returns `UP` over HTTPS.
5. Test registration/invitations, verification resend, password reset, CSV upload,
   manual calendar fallback, driver override, and start/end driving.
6. Keep production registration disabled for the controlled pilot.
7. Configure encrypted database backups and test a restore before storing real data.
8. Limit production access to authorized Six Flags pilot participants.
9. Establish an approved retention period for employee names, IDs, and schedule history.

## Flyway rule

Never edit a migration after it has been applied to a shared or production database.
Create the next numbered migration instead. Historical migrations V2, V3, and V10
were sanitized during early development; an older local database may need to be
recreated as described in `PRIVACY_CLEANUP.md`.
