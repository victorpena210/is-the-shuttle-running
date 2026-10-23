# Driving history is the main workflow

The app records who actually drove, the start and end of each driving segment,
and total driving time. It does not assign driving shifts.

## Daily use

1. Drivers use the existing shuttle NFC tag or QR code to start driving.
2. Pause, resume, hand off, or end driving at the checkpoint. Pauses end a segment;
   resuming starts another, so breaks are excluded from driving time.
3. Managers sign in to the history at `/app`. Filter by date and driver to see
   the actual records and totals. `/driving-records` opens the same page.
4. Choose **Who is driving now?** for live status and active-run controls at
   `/app/live-status`. A manager can close a forgotten run using its actual end time.

Driver accounts, sign-in, NFC/QR links, the driver's recent records, and the
public `/ride/{slug}` pages continue to work. Old planning links redirect to
the current workflow; old planning submissions are rejected before they run.

## Changes in this update

- Manager sign-in now opens driving history instead of the live-status dashboard.
- History comes first in navigation, with a direct link to live status.
- The history controller no longer depends on assignment review or planned-workload
  page services. Its old assignment-outcome action explicitly returns HTTP 410.
- The driver portal no longer loads or selects planned assignments.
- Rider status no longer injects the assignment repository or unused scheduled-start
  and scheduled-end thresholds.
- Live-status actions return to the new live-status page after saving.

Schedule imports, availability uploads, automatic planning screens, and
forecasting jobs are retired. Legacy implementation remains in the source for
compatibility with existing records; it is not part of the active workflow.

## Public rider page

The rider page shows only **The shuttle is running.** or **The shuttle is not
running.**, plus the current driver's first name when a driver is active.
Waiting for passengers and service ending soon display as running; paused,
delayed, unavailable, unconfirmed, and stopped service display as not running.
The page continues to refresh every 20 seconds.

Schedules, operational messages, pickup/help sections, confirmation timestamps,
passenger QR codes, and **Confirm my ride** have been removed from the public
page. The controller no longer creates a passenger-device cookie or reads
passenger confirmation state. The old passenger-confirmation POST and QR image
routes return HTTP 410 without recording a ride. Existing recorded passenger
data is preserved for compatibility.

## Existing installations

Deploy the source using the normal build pipeline and retain the existing
database, driver accounts, organization settings, and tag codes. No database
migration is added or changed. History includes both earlier assignment-linked
driving sessions and newer independent sessions.

## Validation

Focused history, rider-status, reliability, login-routing, and public-name
checks cover the simplified workflow. `DrivingHistoryTests` and
`PublicRiderStatusTests` include these checks in the normal JUnit suite.

Run the standard checks with network access and Docker available:

```bash
node --test src/test/js/*.test.cjs
./mvnw clean verify
```

Smoke-test sign-in, history filtering, NFC/QR start/pause/resume/handoff/end,
the public rider page, and manager closure of an active run.
