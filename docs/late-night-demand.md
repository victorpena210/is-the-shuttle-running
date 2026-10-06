# Late-night transportation demand

The default transportation-demand view now covers **9 AM through 11:59 PM**. Pickups after 9 PM appear in Evening Pickups instead of the collapsed Off-hours section. Overnight and early-morning events remain in Off-hours. The displayed range comes from the effective configuration, so it cannot silently claim a different cutoff than the forecast uses.

Late demand buckets and pickup buffers stop at the end of the selected day instead of wrapping into the next morning. The forecast still shows all late pickups, regardless of whether a driver is available.

## Driver work shifts

Automatic evening plans match demand to the selected driver's uploaded work shift. With a 3–11 PM work shift, Jody can cover late pickups until 11 PM, subject to the existing wardrobe closing rule and actual demand. A 10:45–11 PM pickup window can be covered; an 11:30 PM pickup cannot be assigned to that work shift. That later demand stays visible for a manager to review.

The plan starts with eligible demand. Its end follows the last demand the driver can cover, retaining the applicable pickup buffer and stopping at clock-out. Demand occurring only after clock-out cannot extend an earlier run through a quiet stretch. Morning and required midday opening blocks keep their existing three-hour coverage behavior and counter-staffing checks.

Future untouched automatic assignments recalculate when the dashboard loads. Manual edits and assignments that have already started remain protected.

## Configuration

`TRANSPORTATION_DEMAND_OPERATING_END` now defaults to `23:59`. An explicitly configured value still takes priority. If a deployed view continues to display a 9 PM cutoff after this update, check whether that environment variable is explicitly set to `21:00` and change it to `23:59`.

## Apply

This patch is based on the uploaded October 6, 12:08 PM project, including the earlier calendar-opening and test corrections. Save `shuttle-late-night-demand.patch` in Downloads and run:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/shuttle-late-night-demand.patch &&
git apply ~/Downloads/shuttle-late-night-demand.patch &&
bash ./mvnw clean verify
```

Start Docker Desktop before running the full integration suite. After BUILD SUCCESS, review the changes, commit, and push through the existing deployment process. Reopen the selected date after deployment to recalculate future automatic assignments.

## Verification

Application sources compiled for Java 17 against the uploaded runtime dependencies. Passed: six transportation-demand JUnit tests, 73 fairness/planning checks, 59 wardrobe-hours checks, 69 service-review checks, and eight regression groups including Thymeleaf rendering. Tests cover late pickups, 11:59 PM events, midnight wrapping, Jody's 11 PM clock-out, later uncovered demand, and a midnight-ending work shift. Full Maven/MySQL integration verification must run locally; it was not executed in the editing environment.
