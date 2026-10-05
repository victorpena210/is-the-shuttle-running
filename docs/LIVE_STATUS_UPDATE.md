# Live shuttle status and public driver names

## Changes

- Added **Live shuttle status** immediately after Overview in the manager navigation.
- The status controls appear only in that tab, at `/app#live-status`.
- Schedules and Transportation demand now show their own content without the status controls.
- Status updates and Return to automatic continue to redirect to the live-status tab.
- The live-status tab hides the planning-date label because its controls apply to current service.
- The public rider page shows first names in the active-driver display, passenger confirmation and daily schedule. The passenger-confirmation model uses the same formatting.
- Both `First M. Last` and Dayforce-style `Last, First M.` names are supported. Internal driver records retain the full name.

## Apply

Copy the files from this ZIP's `is-the-shuttle-running` folder into the existing project folder, then build and redeploy using the usual workflow. The archive contains source files and does not include Git history or credentials. No database migration is needed.

Run the focused privacy test locally:

```sh
./mvnw -Dtest=PublicDriverNameTests test
```

After deployment, open `/app#live-status` and `/ride/sfft` to check the new navigation and public names.

## Validation performed

- Passed 12 Java name-formatting checks, compiled with Java 17.
- Passed navigation checks using the actual dashboard JavaScript and section/navigation attributes from the templates: direct links, switching among all five tabs, current navigation state, planning-date visibility, reload and unknown-hash fallback.
- `git diff --check` passed.
- The Maven build could not run because Maven Central was unreachable from the editing environment. Full application compilation and integration tests remain to be run locally or in CI.
