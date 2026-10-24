# Privacy cleanup

The current source tree contains no bundled employee schedule exports or real
driver seed records. Template fallback labels and test examples use fictional
drivers. This describes the source files, not a production database.

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

## Schedule retention for transportation demand

The current demand upload stores anonymous 15-minute clock-in/out counts in
`transportation_demand_windows` for a rolling 12 months. It does not save ordinary
employees' names, IDs, departments, jobs, or locations. Upload metadata is kept in
`schedule_imports`.

For driver suggestions, only employee IDs that exactly match registered drivers
are used to save work hours in `driver_schedule_windows`. These rows contain a
driver reference, upload reference, work date, start, and end. Past source dates
older than 31 days are removed by the nightly demand retention job; future work
hours remain available. Changing a driver's employee ID clears their saved work
hours and requires a new upload. Re-uploading replaces all driver hours and demand
for the work dates present in the file, including overnight carryover.

Legacy raw-upload, historical-snapshot, and driver-confirmed-availability tables
may still contain data from earlier workflows. Their old cleanup and scheduling
workflows are retired. The current upload neither reads nor writes those tables.
Registered drivers and actual driving history remain separate records.

## Private UKG calendar subscriptions

`driver_calendar_connections` stores each registered driver's encrypted subscription
link and refresh status. `driver_calendar_shifts` stores only the current six-week
snapshot of shift start/end times. Every successful refresh replaces that snapshot;
disconnecting deletes both the connection and its shifts. Feed descriptions,
calendar UIDs and raw responses are not stored. Plaintext subscription links are
never returned in HTML or error messages and must never be committed to source.

Managers can connect drivers in their organization; drivers can connect only their
own linked profile. Stored account status and membership are rechecked for every
connection action. Connection links are encrypted with authenticated encryption
bound to both organization and driver. The stable application secret used for this
must be backed up separately from the database.

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

## Public repository history cleanup — October 2026

Earlier commits retained schedule files and real driver seed values after the
working tree had been cleaned. A later audit also found real names and employee
IDs in template fallback text and a test fixture. These need cleaning in both
the current source and every retained branch, not just a new deletion commit.

The cleanup removes the historical `src/main/resources/data/` directory,
including CSV and Numbers files, and replaces the known employee names and IDs
in historical source with fictional demo values. Original commit history is
preserved apart from the affected content and resulting commit hashes.

After a history rewrite, use a fresh clone. Do not merge or push an old clone's
branches, because that can restore the removed data. No production database
purge or Flyway reset is part of this repository cleanup.

Rewriting branches does not guarantee that GitHub has purged cached commits or
references in existing pull requests. Follow GitHub's
[sensitive-data removal procedure](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)
and request Support cleanup where needed. Any downloaded copies remain outside
the repository owner's control.
