# Production monitoring and recovery

This runbook is the operating-day safety net for RoutePulse. It separates
**detection** (know something is wrong quickly) from **recovery** (restore service
or data safely).

## What is implemented in the repository

- Spring Boot Actuator readiness and liveness groups.
- Public, detail-free health probes at `/actuator/health/readiness` and
  `/actuator/health/liveness`.
- Sentry Spring Boot 4 integration, disabled until `SENTRY_DSN` is set.
- No default PII forwarding to Sentry.
- Opaque `X-RoutePulse-Request-Id` response IDs, matching log correlation IDs,
  and a reference ID on the generic error page.
- A five-minute GitHub Actions readiness check that opens one incident issue on
  failure and closes it after recovery.
- Logical MySQL backup, destructive restore, and isolated restore-drill scripts.

## 1. Sentry error reporting

Create a Sentry Java/Spring Boot project and add its DSN to the production
environment as `SENTRY_DSN`. Keep `SENTRY_TRACES_SAMPLE_RATE=0.0` initially; the
first goal is reliable exception alerting rather than paid performance tracing.
Set `SENTRY_RELEASE` to a deploy identifier (for example the Git commit SHA) when
your deployment process makes one available.

RoutePulse does not fail startup if Sentry is unavailable. A production startup
without a DSN writes a warning to the application log.

Recommended Sentry alert: notify on a **new error** or an error that becomes
**frequent again** in the production environment. Do not enable request-body or
default-PII capture for this application. In Sentry project privacy settings,
also enable IP-address scrubbing/prevention so operational errors do not become a
new source of employee or rider identifiers.

## 2. Uptime / readiness alerts

The readiness probe includes application readiness, MySQL connectivity, disk
space, and ping. The liveness probe intentionally excludes MySQL: a database
outage should make RoutePulse *not ready*, but it should not cause a restart loop.

In GitHub repository settings, create this Actions variable:

- `ROUTEPULSE_PRODUCTION_URL` = the public HTTPS origin, with no path.

The `RoutePulse Production Uptime` workflow then checks readiness every five
minutes. A failure makes the workflow red and opens one GitHub issue titled
`[Uptime] RoutePulse production readiness check failed`. When service recovers,
the workflow comments on and closes that issue. Enable GitHub Actions/issue
notifications for the repository so those incidents reach you.

For a dedicated uptime provider later, point it at the same readiness URL and
alert after two consecutive failures to avoid one transient network hop causing
a false alarm.

## 3. Railway database backups

Use Railway's native volume backup feature as the primary backup layer. For the
production MySQL service, configure **Daily**, **Weekly**, and **Monthly** backup
schedules in the service's **Backups** tab. Keep at least one recent manual
backup before any risky production migration.

Important privacy note: a database backup can temporarily retain employee
schedule rows that RoutePulse later purges from the live database. Treat backups
as restricted production data and keep only the recovery retention you actually
need. Never commit a dump into Git.

## 4. Portable logical backup

Railway volume backups restore inside the same project/environment. A logical
MySQL dump is useful before especially risky changes or when you need a portable
copy. Set `DB_BACKUP_HOST`, `DB_BACKUP_PORT`, `DB_BACKUP_NAME`,
`DB_BACKUP_USERNAME`, and `DB_BACKUP_PASSWORD`, then run:

```bash
./scripts/backup-database.sh /secure/path/routepulse-backups
```

The script uses a consistent InnoDB transaction, compresses the SQL, validates
the gzip stream, and writes a SHA-256 checksum. Keep logical dumps encrypted at
rest and outside the repository.

## 5. Restore drill (safe)

A backup is not proven until it can be restored. With Docker Desktop running:

```bash
./scripts/verify-database-backup.sh /secure/path/routepulse-backups/routepulse-YYYYMMDDTHHMMSSZ.sql.gz
```

This creates a disposable MySQL 8.4 container, restores the dump there, confirms
that tables exist and that `flyway_schema_history` is readable, then destroys the
container. It never touches production. Run this drill after establishing backup
procedures and periodically after schema changes.

## 6. Actual restore (destructive)

Do not restore directly over production as your first diagnostic step. Prefer a
new recovery database/service, validate it, then point a controlled deployment
at the recovered data.

If an SQL restore is intentionally required, set the `DB_RESTORE_*` variables
and use the explicit safety flag:

```bash
CONFIRM_ROUTE_PULSE_RESTORE=YES \
  ./scripts/restore-database-backup.sh /secure/path/backup.sql.gz
```

After restoring, verify the migration count, start RoutePulse against the
recovered database, check readiness, sign in, open the manager dashboard, verify
current drivers/assignments, and test the rider status page before reopening
normal traffic.

## Operating-day incident order

1. Confirm `/actuator/health/readiness` and check the uptime incident timestamp.
2. Check Railway application and MySQL status/logs.
3. Check the newest Sentry exception and correlate its timestamp/route with Railway logs;
   if a manager has an error-page reference ID, search Railway logs for that ID.
4. If the app is unhealthy but the database is healthy, redeploy/restart the app.
5. If the database is damaged or data was deleted incorrectly, stop writes before
   attempting recovery. Prefer a restore into a separate recovery target first.
6. Once recovered, verify manager login, driving QR flow, rider status, and the
   current operating-day assignments.
7. Record what failed and what prevented a faster detection/recovery, then update
   this runbook.
