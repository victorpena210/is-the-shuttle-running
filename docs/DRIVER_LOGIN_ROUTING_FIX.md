# Driver login routing fix

Applies to the uploaded project at commit `a48b20c`.

## Cause and correction

The screenshot shows HTTP 403 at `/app`, a manager-only page. Spring Security's saved-request login handler could return a driver to that page instead of visiting `/post-login`, which normally routes drivers to `/driver`.

The login handler now discards saved manager destinations for driver-only accounts. Normal role-based routing then sends them to their driver home. Saved driver GET links, including schedule filters and NFC/QR checkpoint codes, still resume after login. Saved manager links still work for accounts with manager roles. Route permissions remain enforced.

The error page's return button now goes through `/post-login`, so it selects the correct home for the signed-in account.

For immediate access before deployment, open `https://istheshuttlerunning.com/driver` directly.

## Apply and test

With Docker Desktop running:

```bash
cd ~/Desktop/IdeaProjects/RENATUS/is-the-shuttle-running &&
git apply --check ~/Downloads/driver-login-routing-fix.patch &&
git apply ~/Downloads/driver-login-routing-fix.patch &&
bash mvnw clean package
```

After `BUILD SUCCESS`:

```bash
git add src docs &&
git commit -m "Fix driver login routing after saved manager pages" &&
git push
```

After deployment, sign out, open `/app`, and sign in with a driver account. The final destination should be `/driver`. Also check an NFC/QR login link; its checkpoint code should still be present after signing in. Direct access to manager pages remains restricted for driver accounts.

## Validation

The uploaded baseline includes 108 passing tests. New checks reproduce the old saved-manager-page redirect and exercise driver fallback, retained driver query strings, context paths, manager routing, and the error-page link. The real security-filter tests now cover saved manager pages, personal schedule filters, and manager dashboard dates alongside existing remember-me, NFC and CSRF checks.

All main Java sources compile locally and executable routing/template checks run against the app's runtime libraries. The full Maven/MySQL suite requires the user's Mac environment; it has not been run here. No database migration or new configuration is required.
