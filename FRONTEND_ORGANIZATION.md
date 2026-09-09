# RoutePulse frontend organization

The frontend has shared CSS entry points, focused CSS modules, browser ES modules,
and Thymeleaf screens grouped by audience.

## CSS

All styles are under `src/main/resources/static/css/`.

| Entry point or folder | Responsibility |
| --- | --- |
| `site.css` | Shared import hub loaded by screens; preserves the established cascade order |
| `base/` | Variables, reset, typography, accessibility |
| `layout/` | Navigation, app layout, responsive shell, footer and footer branding |
| `components/` | Branding, shared controls and operational components |
| `components/operations/` | Smaller modules imported by `components/operations.css` |
| `dashboard.css` | Dashboard import hub |
| `dashboard/` | Transportation demand and the planning-date control |
| `dashboard/driver-coverage/` | Coverage modules imported by `dashboard/driver-coverage.css` |
| `pages/` | Auth, landing, demo request, rider status, passenger ride and driving records |
| `pages/driving-records/` | Report modules imported by `pages/driving-records.css` |
| `routepulse.css` | Compatibility entry point that imports `site.css` |

Templates load `site.css` plus any page entry point they need. Add shared rules
to the appropriate shared module; add screen-specific rules to that screen's
modules. Preserve import order when changing the cascade.

## JavaScript

All scripts are under `src/main/resources/static/js/`.

| Folder | Files and role |
| --- | --- |
| `shared/` | `date-utils.js`: date parsing, comparison and week calculations |
| `components/` | `dashboard-views.js`, `planning-calendar.js`, `planning-status.js`, `driver-coverage.js` |
| `pages/` | `dashboard.js` and `drivers.js`: page initialization and coordination |

`manager/dashboard.html` loads `js/pages/dashboard.js`; `manager/drivers.html`
loads `js/pages/drivers.js`. Page scripts import the components they initialize.
Pure utilities belong in `shared/`; user-interface behavior belongs in
`components/`; page startup belongs in `pages/`.

## Thymeleaf

All templates are under `src/main/resources/templates/`.

| Folder | Screens |
| --- | --- |
| `public/` | `landing.html` and `rider-status.html` |
| `auth/` | Login, registration, invitation acceptance, verification resend, password reset |
| `manager/` | Dashboard, drivers, driving records, driving QR and invitations |
| `driver/` | Driver home and QR scan |
| `errors/` | Organization access blocked |
| `error.html` | Spring Boot's conventional error template |
| `fragments/` | Shared navigation and footer |
| `fragments/dashboard/` | Overview, upload, import history, planning date and demand sections |
| `fragments/driver-coverage/` | Coverage shell, shift card, availability, timeline and assignment controls |
| `fragments/driving-records/` | Filters, tabs, summary, workload table, record details and outcome review |

The Java controller returns the audience-qualified template name. Public URLs
remain `/`, `/app`, `/drivers`, `/driving-records`, `/driver`, and the other
existing paths.

A shift card keeps its assignment, period labels, driver schedule, and suggested
time parameters. Record detail fragments receive `record` explicitly. Other
fragments use the parent model documented by `DashboardPlan`,
`DashboardModelAssembler`, and `DrivingRecordController`.

Place conditions outside fragment replacements when they control whether the
fragment is included. Thymeleaf processes `th:replace` before `th:if`:

```html
<th:block th:if="${showSchedule}">
    <th:block th:replace="~{fragments/driver-coverage/coverage :: coverage}"></th:block>
</th:block>
```

The cleanup regression tests render the real fragments with populated models,
including unverified calendars, missing shift assignments and driver-filtered
reports. Run `./mvnw clean verify` with Docker available for the full suite.
