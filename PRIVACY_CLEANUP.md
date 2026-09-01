# Privacy cleanup

This copy of RoutePulse contains no bundled driver records or saved schedule data.

The cleanup removed:

- real driver names and employee IDs from historical seed migrations;
- the bundled schedule CSV and its compiled copy;
- compiled build output and operating-system metadata;
- the developer's personal name from the Maven group and Java package namespace;
- organization-specific values used only to backfill legacy driver records.

The project also ignores common local data, exports, logs, database files, environment files,
and private-key formats to reduce the chance of committing sensitive material later.

## Delete data already stored in MySQL

Sanitizing the project files does not alter an existing database. To erase all application
records while preserving the schema and Flyway history, run the included purge script against
the intended RoutePulse database:

```bash
mysql -u shuttleflow_admin -p shuttleflow < scripts/purge-application-data.sql
```

Confirm the database name before running the command. The deletion cannot be undone unless a
backup exists.

## Existing development database

The contents of historical migrations V2, V3, and V10 were intentionally sanitized. Flyway
will detect changed checksums if those migrations were already applied to an existing database.
For this early-stage development project, the cleanest option is to recreate only the
RoutePulse development database and let Flyway build it again from the sanitized migrations.
Do not drop a shared or production database.
