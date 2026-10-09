# Optional movement monitoring

Updated October 9, 2026. Service now follows the recorded driving session, independently of phone location.

- Starting or resuming service keeps the session active until the driver pauses/ends it, another driver takes over, or a manager closes it.
- No two-hour reconfirmation deadline, scheduled-end expiration, 10-minute stationary prompt, or 20-minute inactivity cutoff.
- Missing GPS, denied permission, locked phones and closed browsers do not downgrade rider status or close sessions.
- Location sharing remains optional. Reliable route movement can update the recorded last movement time. Coordinates are not exposed to riders.
- Waiting, Driving, On break and End session controls work without location permission. Waiting remains active even when location is lost.
- Temporary manager overrides continue to apply for their selected duration, then return to the recorded session state.
- Historical automatic closures and manager review notes remain intact. Already-ended sessions are not reopened.
- No schema migration or environment-variable change is needed. Old RIDER_CONFIRMATION_MINUTES and RIDER_OVERRUN_GRACE_MINUTES values no longer affect sessions.

Managers can save the optional route or review historical timeouts at `/operations/movement`.
Use session controls while parked. No phone interaction is required to keep driving service active.
