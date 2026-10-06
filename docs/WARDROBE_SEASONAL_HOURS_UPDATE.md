# Seasonal wardrobe closing hours

## Manager workflow

Open **Driver coverage → Wardrobe hours → Set seasonal hours**.

1. Choose **Summer · 5 PM** or **Fright Fest · 7 PM** to fill the season name and closing time. Other names and times are supported.
2. Set the first and last date of that season, including its year. These dates are entered by the manager; the app does not guess the park's season dates.
3. Save the season. Upcoming automatic assignments are recalculated immediately.

For an exception, select its planning date and use **Change this day only**. A daily override takes priority over any seasonal rule. **Use seasonal hours again** removes that day's exception while preserving its audit history.

Season dates are inclusive. The latest saved seasonal rule wins on overlapping dates. Dates without a matching rule use **7 PM**. Midnight means the end of the selected day. Rules belong to the signed-in manager's organization, and only OWNER, ADMIN and DISPATCHER memberships may change them.

## Scheduling behavior

- Evening driving starts no earlier than the applicable wardrobe closing time, and only while an eligible driver is still on the uploaded work schedule and transportation demand remains.
- A lone roster member can drive after wardrobe closes. Morning still needs two roster members throughout 9–12; midday remains an optional fairness suggestion.
- Saving seasonal or daily hours recalculates the uploaded future planning horizon, including fairness based on the revised driving minutes.
- If all evening demand ends by closing, the app creates no evening assignment and does not report missing required coverage for that period.
- If planning occurs after closing, a new evening assignment begins at the next whole minute with remaining demand, rather than being backdated.
- New manual evening assignments and time edits must respect closing. Existing manager decisions and assignments whose start has passed are preserved and flagged for review if they conflict.
- Changing hours does not start or stop a recorded driving session. Drivers still use the existing checkpoint controls.
- Transportation demand before closing remains visible. It does not create service-review gaps; actual driving and rider evidence remain included. An explicitly preserved earlier assignment retains its promised service-review window.
- Later seasonal edits do not rewrite prior dates' stored hours decisions. A season may begin in the past if it is still active, but the new rule applies from the day it is saved onward.

## Apply this incremental patch

This patch is based on your uploaded `is-the-shuttle-running(20261006-011604).zip`, which already includes the staffing update. Apply the new patch once in the original Git-connected project:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/wardrobe-seasonal-hours-update.patch &&
git apply ~/Downloads/wardrobe-seasonal-hours-update.patch
```

Keep Docker Desktop running and test:

```bash
bash mvnw clean package
```

After **BUILD SUCCESS**:

```bash
git add src docs
git diff --cached --stat
git commit -m "Add seasonal wardrobe hours and schedule evening driving after closing"
git push
```

The prior staffing changes were staged but uncommitted in the uploaded project. This commit can include both updates. After Railway deploys successfully, set your actual Summer/Fright Fest date ranges in the new form. The buttons are presets; they do not activate a season until you save its dates.

## Database and verification

Flyway migration **V35** adds an organization-scoped audit table for wardrobe-hours rules. No existing migration or driving record is rewritten. The migration runs automatically at application startup.

Local validation: all application sources compiled with Java 17; 28 wardrobe-hours checks, 33 fairness/planning scenario groups, 64 service-review checks, 78 driver-insights checks, and 8 cleanup/template regression groups passed. The checks cover date boundaries, daily precedence and reset, organization and role isolation, remaining demand, staffing after closing, stale records, and real Thymeleaf web-context rendering of the manager forms.

Added MySQL integration checks for persistent season/day settings, HTTP permissions and CSRF, recalculation against real imports, and the V35 migration. The full Maven/MySQL suite must run on your Mac; it was not available in this environment.
