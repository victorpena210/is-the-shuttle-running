# Is the Shuttle Running? — rebrand and launch notes

The public product name is **Is the Shuttle Running?**, with **istheshuttlerunning.com** as the intended production domain. This delivery updates the uploaded source; it has not been deployed and does not register or connect the domain.

## Review the design

Open `docs/preview.html` in a browser after extracting the whole project. Links at the top switch between the homepage, sign-in, and sample rider page. These are presentation previews with example data and disabled forms; they do not run the Spring application or connect to your database. The homepage status buttons are interactive. Preserve the project directory structure so CSS, images, and the video resolve.

The design uses charcoal, warm white, signal green, a transit-style shuttle mark, large status typography, and responsive layouts. The homepage leads with the rider's question and explains the manager, driver, and rider workflows. Its example status panel is explicitly labeled; real organization status remains at `/ride/{slug}`.

## Included changes

- Rebuilt public homepage, shared marketing navigation, demo section, FAQs, and status preview.
- Updated app navigation, sign-in and account pages, public rider styling, footer, browser titles, visible help text, notifications, and email subjects/body text.
- New SVG shuttle mark, social sharing PNG with editable SVG source, and video poster.
- Updated public canonicals, structured data, social URLs, sitemap, and robots sitemap reference to the new domain.
- Preserved the working demo-request form's binding, validation, CSRF-enabled action, and success state.
- Retained the existing product video with a visible note that it was recorded as RoutePulse.

## Technical compatibility

The Java package `technology.renatus.routepulse`, main class, Maven artifact ID, database names, Flyway migrations, QR/NFC tokens, URL routes, monitoring identifiers, and request-correlation header remain unchanged. A public rebrand does not require renaming these internal identifiers. All 33 existing SQL migrations are byte-for-byte unchanged. No scheduling, driver assignment, authorization, or riding-confirmation rules were changed.

The Spring production profile already requires `APP_BASE_URL`. It remains required and is not silently replaced. Mail sender and recipient settings are unchanged, so no unverified email address is introduced.

## Before production cutover

1. Confirm you own `istheshuttlerunning.com`, add it to the current hosting service, and configure the DNS records that host provides. Confirm HTTPS works before announcing the change.
2. Set production `APP_BASE_URL=https://istheshuttlerunning.com` once the new hostname is ready. Deploy this version at the same cutover: its canonical and sitemap URLs already use the new domain.
3. Keep `routepulse.technology` under your control. At the hosting or edge layer, redirect the old hostname to the new one while preserving the full path and query string. This is especially important for existing `/ride/{slug}`, driver checkpoint, verification, invitation, and password-reset links. Avoid changing tokens or slugs. Test existing links before relying on redirects. Users will generally need to sign in on the new hostname because browser cookies are domain-scoped.
4. Keep the existing verified `MAIL_FROM`, SMTP configuration, and `DEMO_REQUEST_RECIPIENT` unless you separately configure a new sender. Send a test verification, invitation, and reset message to confirm their links use the new hostname.
5. Verify sign-in, manager navigation, driver checkpoint scans, every real rider status, ride confirmations, and the demo request against a non-production organization. Test mobile widths and keyboard navigation, including reduced-motion settings.
6. Update external links and QR/NFC destinations as practical. Preserve redirects for previously printed or distributed links. Review analytics host settings, external monitoring targets, and search-engine domain migration after cutover.
7. Record a new walkthrough when convenient; the current video deliberately retains a former-name note.

DNS, hosting changes, redirects, and sending test emails were not performed by this source-code change.

## Verification

Completed in this environment:

- Resolved all 70 production template asset references and all CSS imports.
- Parsed the public JSON-LD blocks successfully.
- Confirmed all 33 existing Flyway migrations are unchanged.
- Confirmed Java edits are only the product-name replacement inside string literals; technical IDs and logic are unchanged.
- Checked the new JavaScript syntax and exercised the three preview states and accessible button selection using a local harness.
- Preserved the demo form field names, constraints, action, and binding.

Not completed here:

- `bash mvnw -B -ntp -DskipTests compile` could not resolve the Spring Boot 4.1.0 parent because `repo.maven.apache.org` was unreachable (DNS/network restriction). This was not a compiler error, and no successful build is claimed.
- Browser screenshot/responsive verification was unavailable: the cloud browser blocked local preview URLs. The included HTML previews are supplied for local review.
- Live Spring/Thymeleaf rendering, database-backed integration tests, form delivery, and production domain behavior still require your environment.

Run from the project root on your Mac:

```bash
chmod +x mvnw
./mvnw -B -ntp verify
```

Follow the existing README for the local MySQL and environment setup. Tests using Testcontainers require Docker. Run the app with your existing development configuration and review `/`, `/login`, the app dashboard, and a real `/ride/{slug}` page before deploying.

Suggested commit message:

```text
feat: rebrand RoutePulse as Is the Shuttle Running and redesign public experience
```
