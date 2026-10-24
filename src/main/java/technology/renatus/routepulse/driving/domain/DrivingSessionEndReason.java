package technology.renatus.routepulse.driving.domain;

/** Why an actual driving session stopped. */
public enum DrivingSessionEndReason {
    PAUSE,
    COMPLETED,
    INACTIVITY_TIMEOUT,
    DURATION_LIMIT,
    HANDOFF
}
