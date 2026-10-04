# NFC driver check-in and 90-day sign-in

The NTAG215 tags in your screenshot can hold the checkpoint URL. One tag can be
shared by the shuttle's drivers: the signed-in account identifies the driver.
The current app uses an organization-wide checkpoint, not a separate tag per vehicle.

The flow is **tap tag → open link → confirm Start driving → saved driving record**.
The confirmation is intentional: opening or previewing a URL must not start or
end a session. The recorded start time is the time the confirmation reaches the server.

## 1. Deploy this update

The supplied project includes opt-in persistent sign-in and updated setup help.
The NFC start/pause/resume/end workflow already existed. No database migration is needed.

1. On your Mac, generate a signing secret once:

   ```bash
   openssl rand -hex 32
   ```

2. In the Railway **application service** Variables, add `REMEMBER_ME_KEY` with
   that generated value. Keep it private and keep the same value across deploys
   and replicas. Do not put it in Git, screenshots, or on the NFC tag.
3. Confirm `SPRING_PROFILES_ACTIVE=prod` and
   `APP_BASE_URL=https://istheshuttlerunning.com`. The optional
   `REMEMBER_ME_DAYS` setting defaults to `90`. Other existing variables stay in place.
4. Apply the code changes to your existing local project. The archive contains
   the full project and `docs/NFC-CHANGES.patch`. From your existing project root
   (the folder with `pom.xml`), you can use the patch without replacing your Git history:

   ```bash
   git apply --check /path/to/extracted/is-the-shuttle-running/docs/NFC-CHANGES.patch
   git apply /path/to/extracted/is-the-shuttle-running/docs/NFC-CHANGES.patch
   ```

   Replace the example path with the actual extracted path. If the check reports
   conflicts, review those differences before applying; do not overwrite unrelated work.
5. Run focused tests (no Docker needed):

   ```bash
   bash mvnw -Dtest=RememberMeSecurityTests,DriverCheckpointServiceTests,DrivingSessionCheckpointMethodTests test
   ```

   With Docker Desktop running, run `bash mvnw clean verify` for the full suite.
   Review the diff, commit and push through your existing Railway deployment flow.
   Verify `/actuator/health/readiness` and the sign-in page after deployment.

**Verification status for this delivery:** source/configuration and patch checks
were completed. Maven could not download the existing Spring Boot 4.1.0 parent
because `repo.maven.apache.org` could not be resolved from the editing environment.
Compilation and the new tests therefore have not run. Run the commands above before deployment.

## 2. Prepare each driver

- Register/link the driver to their own user account with the DRIVER role and an
  active driver profile. Have them accept the driver invitation and set their password.
- Give them an assignment with an eligible driving window. The existing app
  checks assignment timing; an arbitrary off-window test can correctly be blocked.
- On their own phone, have them sign in at
  `https://istheshuttlerunning.com/login` and select **Keep me signed in for 90 days**.
- Use the normal browser that opens NFC links. A different browser, an isolated
  home-screen app, or private browsing may use a different cookie store.

## 3. Program the tag

1. Sign in with your OWNER or ADMIN account and open
   `https://istheshuttlerunning.com/driving-qr`
   (also available through Drivers → Manage NFC + QR).
2. Click **Copy NFC URL**. It has the format
   `https://istheshuttlerunning.com/driver/tap?code=YOUR_ORGANIZATION_CODE`.
   Copy the real full URL from the app; the example code above will not work.
3. In an NFC-writing app on an NFC-capable phone, choose **Write**, add a
   **URL/URI/website record**, and paste the copied URL. Button names vary by app.
   Write only this URL record, not a plain-text record, password, or automation.
4. Hold the tag to the phone's NFC reading area until writing succeeds. Read it
   back and confirm the entire URL matches. Keep it rewritable while testing.
5. Test in its intended mounting location before attaching it permanently. If
   scanning stops working near metal, try a nonmetal surface or suitable spacer.

## 4. Test a real start

1. During an eligible assignment window, the signed-in driver taps the tag.
   Open the link notification if the phone shows one; unlocking may be required.
2. Check the displayed driver name and assignment, then press
   **Confirm start driving**. Internet access is required.
3. Wait for the saved-start message. As a manager, open **Driving records** and
   check the actual driver, start time and NFC method.
4. Tap again: the active driver can choose **Pause driving** or **End driving**.
   A later tap can resume; a different driver is asked to confirm a handoff.
5. Check persistent sign-in by closing/reopening the browser and then, after
   the normal 30-minute idle session expires, tapping again. For a stronger
   check, repeat after an application redeploy with the same signing key.
6. Confirm logout: sign out, tap again, and verify login is required. A signed-out
   tap should return to its original checkpoint after successful login.

## How persistent sign-in behaves

The normal production session remains 30 minutes. Selecting the checkbox stores
an HttpOnly, HTTPS-only, SameSite=Lax signed cookie that can restore authentication
for up to 90 days from password sign-in. It does not extend forever on every tap.
The label follows `REMEMBER_ME_DAYS` if you change it (supported range: 1–365).

Logout clears this browser's remembered sign-in. Clearing cookies, changing the
password, expiration, or changing the signing key requires a new sign-in.
Account status and roles are reloaded when restoring authentication. Existing
driver-active and organization-code checks still apply when recording a session.
A copied persistent cookie remains valid until expiry, password change, or key
rotation; logout does not centrally revoke copies. There is no device-management UI.
Use the option only on personal devices. Browser privacy settings may clear cookies earlier.

The NFC URL itself is copyable and is not proof of physical presence. Driver
authentication and confirmation are still required. Regenerating the checkpoint
code invalidates the old tag URL; rewrite every tag and reprint the QR afterward.

## Reference documentation

- NXP NTAG213/215/216: https://www.nxp.com/products/NTAG213_215_216
- Spring Security remember-me: https://docs.spring.io/spring-security/reference/servlet/authentication/rememberme.html
