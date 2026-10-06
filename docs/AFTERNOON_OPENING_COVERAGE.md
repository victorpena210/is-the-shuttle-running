# Afternoon opening coverage

An afternoon-start day now gets automatic opening coverage without waiting for wardrobe to close, provided the counter can remain staffed. This is inferred from uploaded schedules and demand; it is not hardcoded to Fridays or to the public park opening time.

## Rules

- There must be no morning arrival demand, and the driver roster must start in the afternoon. With the default demand boundaries, arrivals at exactly 12:00 PM still count as morning; an afternoon-only test schedule must start after noon.
- The first midday or evening demand period overlapping that roster is opening coverage. Driving starts with actual demand, never an early buffer.
- Until the configured wardrobe closing time, the assigned driver must be on the clock and another distinct active roster member must be scheduled and available at the counter throughout every driving run.
- One available roster member stays at the counter. Inactive drivers, non-roster employees, duplicate rows, and someone already assigned to drive cannot provide backup.
- Staffing handoffs and split work shifts are respected. Driving can start when backup arrives, stop when backup leaves, and resume during another safely staffed demand window.
- After wardrobe closes, one scheduled driver can cover remaining demand. Seasonal and daily closing settings still apply; midnight means the end of the day.
- Among drivers with equal safe demand coverage, the existing workload fairness ranking chooses the driver. The planner prioritizes covering more demand when availability differs.
- Only the opening period receives this exception. Later evening service still waits for closing; midday on ordinary morning-start days remains optional.
- Closed or unverified dates still require the existing calendar approval. Future plans update chronologically; new assignments are never backdated.

The coverage card is labeled **Opening coverage**. When staffing prevents it, the page explains that the available person stays at the counter. The system reserves availability for the counter rather than creating a separate counter-assignment record.

Untouched future automatic assignments are updated or withdrawn when staffing changes. Manager decisions and driving history remain saved, with a warning if they conflict with the current rules. Manual assignment changes must also preserve counter coverage. Driver My Schedule automatically includes these saved driving assignments.

Service Review retains raw demand and actual driving evidence. It evaluates eligible opening demand, including an unassigned opening period, while exempting windows in which counter coverage prevents driving. Promised service from protected assignments remains reviewable.

## Apply to the original checkout

Download `afternoon-opening-coverage.patch`, then run:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/afternoon-opening-coverage.patch &&
git apply ~/Downloads/afternoon-opening-coverage.patch &&
bash mvnw clean package
```

After `BUILD SUCCESS`:

```bash
git add src docs &&
git commit -m "Schedule afternoon opening coverage with wardrobe backup" &&
git push
```

The incremental patch is based on commit `8b38b7e` from the uploaded project. The updated source retains the driver-login fix already present in that commit. It does not require a database migration or new setting. The accompanying ZIP contains the updated project source without build output or Git internals; keep the original Git checkout when applying the patch.

## Validation

- All application Java sources compiled against the uploaded application dependencies.
- 55 planning checks passed, including 16 new late-opening scenarios.
- 69 service-review checks passed, including opening requirements and counter-coverage exemptions.
- 8 regression groups passed, including real Thymeleaf rendering of staffed and blocked opening cards and coverage alerts.
- Added a database integration test using imported CSV schedules to verify that inactive and non-roster employees do not count, two roster members enable opening coverage, and a revised one-person roster removes untouched automatic coverage.
- The complete Maven/MySQL suite must run in the original checkout. This environment lacks the cached Maven parent/test dependencies and cannot run that suite. The uploaded baseline reports 112 tests with no failures or errors; those reports predate this change.

After deployment, open a future afternoon-start date in Driver Coverage. Two active roster members with overlapping demand should produce Opening coverage before closing. With one roster member, pre-closing driving should be withheld. Verify the assigned driver's My Schedule displays the saved run times.

## Integration-test correction (October 6)

The first full build after the update ran 113 tests with one error. The new opening integration test started its sample roster at exactly noon, which the existing demand services classify as morning arrivals. That contradicted the test's afternoon-only premise, so no midday opening assignment was created.

The corrected fixture starts at 12:30 PM and explicitly verifies that the generated opening assignment starts at 12:30 PM. A demand-service regression test covers both noon and 12:30 PM. Production scheduling rules are unchanged.

If the original feature patch is already applied, download `afternoon-opening-test-fix.patch` and run:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/afternoon-opening-test-fix.patch &&
git apply ~/Downloads/afternoon-opening-test-fix.patch &&
bash mvnw clean package
```

The correction was checked locally against the real demand bucketizer and applies cleanly to the previously supplied update. The full Maven/database suite still needs to run on the Mac; the prior log's 112 passing tests do not establish that the corrected integration test has passed.
