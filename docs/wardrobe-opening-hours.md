# Opening coverage and late park openings

A verified **5 PM park opening** automatically selects **3–6 PM required midday opening coverage**. This applies on any weekday, even if unrelated employees have morning shifts. A verified **11 AM or noon park opening** selects a **9 AM wardrobe opening and 9 AM–noon opening coverage**. No new wardrobe setting is needed for these calendar rules. A saved opening time for one specific day or weekday takes priority; general every-day seasonal hours receive the calendar-based opening time. The saved closing time is unchanged. Other park opening times retain configured hours.

| Operating day | Opening service | Midday behavior |
| --- | --- | --- |
| Park opens 5 PM | 3–6 PM, in the Midday opening coverage card | Required and automatically assigned when staffed |
| Park opens 11 AM or noon | 9 AM–noon, in the Morning / Opening coverage card | Other midday service stays optional |
| Explicit day or weekday opening time | Three hours from that opening, ending earlier if wardrobe closes | Afternoon openings use the Midday card |

Opening service covers the full configured block when there is demand in that block and staff can cover it. A 5 PM demand-category boundary does not split the 3–6 PM assignment. Demand-free protected breaks may remain within the displayed start/end times. Partial work shifts or gaps in counter staffing can shorten or split the driving intervals.

The planner prefers keeping an existing evening driver's allocation when adding opening coverage. For a two-person Friday roster with Jody assigned in the evening, the other eligible driver (Victor in the reported example) receives opening coverage. It uses roster IDs and the current assignments, with workload ranking for remaining choices; no employee names are hard-coded. Normal-day rotation and eligibility checks continue to apply.

## Overlapping assignments

Different drivers may have overlapping assignments. The same driver cannot cover two assignments at once. Until wardrobe closes, one additional scheduled driver-roster member must remain available at the counter throughout every driving interval. With two people working, one drives and one staffs the counter. Two people driving simultaneously before closing need a third roster member at the counter.

An opening run may extend from midday into evening hours. Later automatic service still starts with demand at or after wardrobe closing; managers may arrange earlier overlapping evening service if demand and counter staffing allow it. Adjacent assignments may touch at an end/start time.

## Saved assignments and overrides

Untouched future automatic plans recalculate when the dashboard loads. Old automatic afternoon opening plans stored in the Morning slot move to the required Midday slot. Manual decisions, started work, and historical assignments stay saved; conflicts are flagged for review. A preserved conflicting manual assignment may need a manager adjustment before a new opening run can be assigned.

The calendar must be verified open, or a manager must have recorded an open override, before automatic planning is allowed. An unknown calendar does not invent a 5 PM opening. One-day and weekday opening overrides are available under **Driver coverage → Wardrobe hours**. Reset a conflicting day override or correct a weekday rule if it prevents the expected 3–6 PM run.

No new database migration is needed beyond the existing V36 wardrobe opening-hours migration.

## Apply this update

Save `shuttle-late-opening-fix.patch` in Downloads. In the original Git checkout:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/shuttle-late-opening-fix.patch &&
git apply ~/Downloads/shuttle-late-opening-fix.patch &&
bash ./mvnw clean verify
```

This patch is based on the uploaded October 6 source, including the previous wardrobe-hours changes. If the patch check fails, stop and inspect the error. Start Docker Desktop for the MySQL integration checks. After verification succeeds, review `git diff --stat`, commit the update, and push through the project's normal deployment process. Open Friday's Driver coverage tab after deployment to trigger recalculation.

## Validation

Application sources compiled for Java 17 against the uploaded runtime dependencies. Passed: 69 fairness/planning checks, 59 wardrobe-hours checks, 69 service-review checks, and eight regression groups including Thymeleaf rendering. These checks cover required midday planning, calendar fallback despite unrelated morning demand, preservation of the existing evening driver, stable replanning, normal-day optional midday, overlap with different drivers, rejection of same-driver overlap, counter staffing, rendering, and reporting.

The MySQL integration test was updated for the Midday opening slot and reporting totals. Full Maven/MySQL verification must also be run in the local development environment; it was not completed in the editing environment.
