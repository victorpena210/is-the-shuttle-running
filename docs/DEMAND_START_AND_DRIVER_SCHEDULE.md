# Demand-based start times and driver schedules

This patch applies to the uploaded project at commit `8f1861f` (seasonal wardrobe hours and login test fix).

## Driving start rules

- Evening assignments start with the first remaining demand window at or after the selected date's wardrobe closing time. The old early-start buffer is removed. The existing pickup end buffer remains.
- Closing during a demand window permits driving from closing onward. Closing during a gap means waiting for the next demand window.
- A driver's uploaded work shift must overlap actual demand. Working only in a quiet gap or pickup end buffer cannot generate an assignment. A late-arriving driver's assignment starts with the first demand they can cover.
- Midday remains an optional fairness suggestion. It now follows the same wardrobe closing cutoff and retains separate driving windows. If all midday demand ends before wardrobe closes, no suggestion is offered.
- Today's new plans use upcoming time; elapsed windows are not assigned retroactively.
- Morning staffing still requires two driver-roster staff, with one available to cover wardrobe. Seasonal closing rules and daily overrides continue to apply.
- Manager-created or edited midday/evening assignments cannot start before closing or before the first eligible forecast demand window. The existing manual extra-service path remains available after closing when no forecast exists for the period.
- Untouched future automatic assignments are updated during normal planning. Existing manual/reviewed assignments and recorded history remain saved; conflicting start times are flagged in Driver coverage for manager review.

Examples:

| Wardrobe closes | Remaining demand | Planned start |
| --- | --- | --- |
| 5 PM | First pickup window at 5:30 PM | 5:30 PM |
| 7 PM | Next pickup window at 8 PM | 8 PM |
| 7:05 PM | Demand window from 7–7:15 PM | 7:05 PM |
| 5 PM | Midday demand ends at 4:30 PM | No midday suggestion |

## Driver access

Drivers have a **My schedule** navigation link and a shortcut on their home page at `/driver/schedule`.

- Defaults to today through the next 27 days; includes a next-7-days shortcut and custom inclusive ranges up to 93 days.
- Shows only the authenticated driver's saved assignments in their current organization, ordered by date and start time.
- Lists driving windows, protected breaks, and scheduled/in-progress status. Completed, cancelled, excused and missed assignments are excluded.
- Optional midday suggestions are not listed until accepted by a manager.
- Viewing the page does not generate assignments or reserve fairness workload. A manager must upload schedules and plan coverage first.
- Missing uploaded schedules and available schedules without personal assignments have distinct empty states.
- Drivers cannot use request parameters to select another driver or organization.

## Apply and validate

Keep Docker Desktop running, then:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/demand-start-and-driver-schedule.patch &&
git apply ~/Downloads/demand-start-and-driver-schedule.patch &&
bash mvnw clean package
```

After `BUILD SUCCESS`:

```bash
git add src docs &&
git commit -m "Align driving starts with demand and add driver schedules" &&
git push
```

After deployment, open Driver coverage to refresh future automatic plans and review any preserved assignments flagged for early starts. Sign in as a linked driver and open My schedule. No database migration or new environment variable is needed.

## Verification

All main Java sources compile with Java 17. Local executable checks cover planning/fairness, seasonal hours, service review, existing driver insights, dashboard templates, and the new schedule service/controller/templates. New MySQL/HTTP tests cover personal schedule query scoping, date ranges, role restrictions, and read-only access; the existing seasonal integration test now expects the first demand after closing.

The complete Maven/MySQL suite must be run locally with the command above; its test dependencies and Docker are unavailable in this execution environment. The uploaded baseline reports all 106 existing tests passing before these changes.
