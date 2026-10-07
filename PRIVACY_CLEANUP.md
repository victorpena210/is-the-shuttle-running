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

## Automatic schedule retention for forecasting

RoutePulse now treats uploaded employee schedules as temporary operational data.
After an operating day is complete, the nightly retention job first creates an
anonymous `historical_demand_snapshots` record plus 15-minute arrival/departure
windows. Those records contain counts and demand timing only; they do not retain
employee names, employee IDs, departments, jobs, or pickup locations.

After the snapshot is safely persisted, every raw `uploaded_shifts` row for the
completed date is permanently deleted, including superseded schedule-upload
versions. Registered drivers, driver assignments, driving sessions, and schedule
import audit metadata are stored separately and are not deleted by this job.

Anonymous demand snapshots are retained for a rolling 12 months by default so a
future forecasting engine can learn weekday, seasonal, peak-window, arrival, and
departure patterns without requiring long-term storage of ordinary employees'
schedule identities. Configure the window with `FORECAST_HISTORY_MONTHS`.


## Driver-confirmed schedule updates

Schedule screenshots are processed locally with Tesseract using temporary files;
the application deletes the temporary images and extracted text files after each
read. Images are not published, saved in the database, or sent to a third-party OCR
service. The review page displays extracted text only to the submitting driver.

`driver_availability_revisions` stores confirmed dates, work hours, previous hours,
imported comparison hours, confirmation time, actor, and screenshot SHA-256 hashes
(not image bytes). These registered-driver operational audit records are retained
with driver records; they are separate from the ordinary employee CSV rows removed
by nightly retention. `scripts/purge-application-data.sql` includes this table.
