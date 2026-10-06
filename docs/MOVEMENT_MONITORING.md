# Movement monitoring

Implemented October 6, 2026. This adds optional, per-session browser location monitoring to the existing driver, manager, and rider workflows.

## Enable after deployment

1. A manager opens **Live shuttle status → Movement monitoring & timeout reviews** (`/operations/movement`).
2. Save the shuttle route as latitude,longitude pairs, one per line, in travel order. Include turns and pickup stops; repeat the first point to close a loop. Use 2–100 points, with adjacent points within 10 km. The route belongs only to that organization.
3. The current driver starts a session using the existing NFC/QR workflow, opens `/driver`, and taps **Enable location for this session** while safely parked.
4. Allow browser location permission. Keep the device in the shuttle, with the driver page open. Enabling a session makes missing location evidence unconfirmed immediately, including when permission is denied.
5. Use **Waiting for passengers**, **On break**, or **End session** only while safely parked.

A saved route is required for automatic movement confirmation. Without a route, reliable stationary updates can still prompt and time out, but movement does not automatically confirm service. Existing sessions remain on their current manual-confirmation workflow until their driver enables monitoring. Starting or taking over a new session requires enabling that new session; a stale page cannot track another driver's session.

## Behavior

| Evidence | Result |
| --- | --- |
| Plausible displacement on the saved route, beyond GPS uncertainty | Update the real service confirmation time and retain running service. |
| 10 minutes continuously stationary with reliable fixes | Show a check-in on the driver page with a safely-parked reminder and closing deadline. |
| Driver selects Waiting for passengers | Keep the session active, show Waiting at pickup, and start a new observation period. A new prompt appears after another 10 stationary minutes. |
| 20 continuously observed stationary minutes with no response | Close the session as Inactivity timeout — needs review. A prompt always gets at least 10 minutes to be answered, even if the scheduler was delayed. |
| Driver selects On break | Close the current driving segment with PAUSE and show temporarily paused service. Resume through the existing NFC/QR checkpoint. |
| Driver selects End session | Close normally through the existing session lifecycle. |
| Location disappears, becomes inaccurate, jumps implausibly, or leaves the configured route | Show Status unconfirmed, retain the last actual service-confirmation time, and reset stationary continuity. Do not auto-close. |
| Location returns | Begin a new continuous observation period; time without data never counts as inactivity. |

Fresh explicit manager operational overrides (such as a vehicle-related pause or no service) retain their existing priority. An ending-soon override cannot invent fresh active service. Rider pages continue refreshing every 20 seconds; refreshing never confirms service.

A timeout closes the software session, not a proven actual service end. Rider status becomes **Status unconfirmed** after an automatic close rather than claiming physical service definitely stopped. The last real confirmation is retained. A fresh session or manager operational update can establish the current status.

## Thresholds and reliability

- Browser requests a new, high-accuracy fix every 15 seconds while sharing; it never uploads a cached coordinate as a heartbeat or queues positions for later replay.
- A fix must be no older than 45 seconds, at most 5 seconds in the future, and newer than the last accepted device timestamp.
- Reported accuracy must be 35 meters or better. The whole accuracy circle must lie within the 150-meter saved-route corridor when a route exists.
- Feed freshness expires after 90 seconds. Reported permission or location errors mark it unconfirmed immediately when the server can be reached.
- Route movement needs displacement over `max(40 meters, both accuracy radii + 10 meters)`, observed over at least 10 seconds. Speeds or jumps over 35 m/s are rejected. A reported speed above 1.5 m/s prevents an inactivity timeout, but speed alone does not confirm running service.
- The server checks monitored active sessions every 30 seconds and also evaluates each received fix. The prompt and automatic closure can occur up to approximately one server-check interval after the threshold; the driver UI polls every 15 seconds.
- All deadlines use server time. Organization locks serialize location updates, responses, starts, handoffs, and the timer job. Multiple app instances cannot independently close the same session twice.
- An explicit waiting or normal running confirmation restarts the inactivity check. A human response alone does not restore a lost location feed for a monitored session.

Browser location needs HTTPS (or localhost) and permission. Screen locking, browser suspension, navigating away, connection loss, and power management can stop updates. This implementation handles those gaps as unconfirmed; it is not an always-on hardware tracker and does not include background push notifications. Driver-device location is operational evidence, not proof of employee attendance or work.

## Records and access

- Automatic ends use `AUTOMATIC` / `INACTIVITY_TIMEOUT`, with no fictitious human end actor.
- Scheduled timeouts leave the assignment pending review rather than awarding normal completion or makeup credit. Recorded session duration is still elapsed session time, not GPS-derived driving time.
- Managers see a Needs Attention item, an outstanding-review list, and a note field. Reviewing records the manager, time, and note while retaining the original reason and timestamps. A review does not change live service or automatically certify completion.
- Scheduled and unscheduled records and driver insights display the timeout reason. Scheduled driver rows flag outstanding reviews.
- Coordinates are available only to authenticated writes from the current session's driver. Status API responses and rider pages do not expose them. Only the current reference/latest points are stored; they are cleared when the session ends. No location trail is collected.
- Routes and reviews require the organization's owner, administrator, or dispatcher. Existing Spring Security role rules and CSRF protection apply to all new routes.

## Database and validation

Flyway migration `V38__movement_monitoring.sql` adds nullable fields to existing organizations and sessions. No earlier migration was edited; existing records do not require backfilling.

Validation performed for this update:

- All 239 main Java source files compiled for Java 17 against the runtime dependency jars included in the uploaded application.
- 32 JUnit tests passed: 29 movement tests, two existing checkpoint tests, and the existing reliability test (which also performs 57 checks).
- Movement coverage includes time boundaries, delayed scheduler, no permission, GPS gaps, bad accuracy, route deviation, GPS jumps/jitter, replayed fixes, waiting, break/end, timeout attribution, review, ownership, stale handoff pages, public status, Thymeleaf rendering, and Hibernate mappings/query parsing with the MySQL dialect.
- Four dependency-free JavaScript tests passed using browser API fakes, including actual request bodies, CSRF headers, permission denial, waiting, prompt, pause/end, automatic closure, and stopping telemetry.
- A full Maven dependency-resolved build and live MySQL migration/concurrency run could not be executed here. Browser launch was blocked by the execution environment, so no real-device or visual browser validation is claimed.

Before deployment, run in the project folder with Java 17+ and Docker available for its existing integration tests:

```bash
bash mvnw clean verify
node --test src/test/js/driver-movement.test.cjs
```

Then check a staging device on the real route: start monitoring while parked, allow location, verify movement confirmation, park through the 10-minute prompt, select waiting, and separately leave a prompt unanswered through the 20-minute timeout. Confirm that denying permission, locking the phone, or closing the page produces unconfirmed status without ending the session. Check the manager review and public rider page. Do not interact with the device while driving.
