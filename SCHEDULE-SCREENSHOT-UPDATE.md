# Driver schedule screenshots and availability

Drivers now have **Update my work schedule** on their home and schedule pages.
They can upload up to three PNG/JPEG screenshots, correct the extracted dates and
hours, and explicitly confirm the update. Manual entry is also available.

## What changes

- Only the signed-in driver's confirmed dates change. Omitted dates keep their
  existing hours. An explicit **Off / unavailable** entry removes that day's work
  availability. Include all shifts on a date when submitting split shifts.
- Confirmed hours override imported hours for driver availability. Full employee
  CSV imports still determine passenger demand; a personal screenshot is not a
  replacement for the full employee roster.
- Existing pending assignments on affected dates are held for manager review,
  including assignments potentially affected by wardrobe counter staffing. The
  original drivers, planned times, manual decisions, and driving history remain.
- **Schedule updates** in manager navigation shows differing imported hours,
  affected assignments, and replacement options that pass current work-hour,
  other-duty, and counter-coverage checks. Managers use the existing coverage
  controls to change drivers or times. A covered assignment can be confirmed in
  the review panel; that confirmation preserves it as a manager decision.
- Later CSV imports cannot silently replace a personal update. Managers may
  explicitly choose **Use latest import for this date**. Every confirmation and
  reversion is stored as an append-only revision.
- Driver and organization identity come from the signed-in account. CSRF remains
  enabled. Review drafts expire after 30 minutes; stale or repeated submissions
  cannot overwrite a newer confirmed revision.

## Reading screenshots

Tesseract reads screenshots locally on the application server. No API key or
external OCR account is required. Docker installs Tesseract and English language
data automatically. On your Mac, install it once for local development:

```bash
brew install tesseract
```

`SCHEDULE_OCR_COMMAND` optionally specifies a different executable path.

Use a clear **daily list** screenshot with readable dates and AM/PM. The parser
recognizes month-name, numeric, and ISO dates, explicit days off, and unambiguous
12-hour or 24-hour time pairs. Choose the schedule year when it is omitted from the
image. Calendar grids and unclear times need manual correction. Confirm the actual
schedule, including AM/PM and year, before saving. File limits: 5 MB per image,
three images, 12 MB total, and 60 work entries per confirmation.

Temporary image and OCR output files are deleted after reading. Screenshot bytes
are not retained. The database stores confirmed work hours, previous/imported
comparison hours, actor, time, source, and image hashes for audit. See
`PRIVACY_CLEANUP.md` for retention details.

## Install this update

Download `driver-schedule-screenshots.patch` to Downloads, then run:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running

git status --short
git apply --check ~/Downloads/driver-schedule-screenshots.patch &&
git apply ~/Downloads/driver-schedule-screenshots.patch
```

Run verification with Docker Desktop running for the MySQL integration tests:

```bash
sh mvnw clean verify
```

Using `sh mvnw` avoids the executable-permission issue with the wrapper. Once
verification succeeds, review and push the change:

```bash
git diff --stat
git add Dockerfile PRIVACY_CLEANUP.md SCHEDULE-SCREENSHOT-UPDATE.md scripts/purge-application-data.sql src
git commit -m "Add driver schedule screenshot updates and availability review"
git push
```

The updated full ZIP is an alternative source copy; apply the patch OR copy the
updated files, not both. The ZIP excludes Git history, build output, macOS
metadata, and duplicate backup folders.

## Deployment

The Railway Docker deployment installs OCR with the updated Dockerfile. Flyway
runs `V39__driver_schedule_updates.sql` on application startup, adding the revision
table and assignment-review hold flag. Keep your existing database configuration.
Take your usual database backup before deployment and let Flyway run normally.
For deployments that do not use this Dockerfile, install `tesseract-ocr` and English
language data in the runtime image first; manual entry remains available if OCR
is missing.

After deployment, sign in as a driver, upload a daily-list screenshot, correct the
preview, and confirm. Sign in as a manager and open **Schedule updates**. Verify a
changed shift flags the original driving assignment, and that a subsequent CSV
import leaves the driver-confirmed hours in use until explicitly reverted.

## Verification performed for this delivery

- All production Java sources compiled with Java 17 against the dependency JARs
  bundled in the uploaded application.
- 35 focused checks passed, including a real Tesseract screenshot read, incomplete
  extraction, AM/PM, explicit off days, split/overnight shifts, stale submissions,
  effective availability, preserved assignment times, and manager reversion.
- 34 existing upcoming-schedule checks and 73 existing fairness/planning checks
  passed (142 checks in total, including the new checks above).
- Both new Thymeleaf pages rendered with populated review/conflict data.
- JavaScript syntax and patch whitespace checks passed.
- Additional MySQL/MockMvc integration tests cover authenticated access, CSRF,
  account/organization isolation, persisted revision/assignment behavior, and replay.
  They are included for the normal Maven test suite.

The full Maven/MySQL suite could not run here because Maven Central was
unreachable and Docker is unavailable. Browser-based mobile layout verification
was blocked by the environment's socket restrictions. Run `sh mvnw clean verify`
on your Mac before deploying.
