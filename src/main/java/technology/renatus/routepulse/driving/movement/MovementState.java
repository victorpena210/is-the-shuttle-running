package technology.renatus.routepulse.driving.movement;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.Instant;

/** A bounded snapshot, not a location history. All inactivity decisions require continuous evidence. */
@Embeddable
public class MovementState {
    public static final long FRESH_SECONDS = 90;
    public static final long PROMPT_SECONDS = 600;
    public static final long TIMEOUT_SECONDS = 1200;
    @Column(name="monitoring_started_at") private Instant startedAt;
    @Column(name="location_captured_at") private Instant capturedAt;
    @Column(name="location_received_at") private Instant receivedAt;
    @Column(name="location_reliable_at") private Instant reliableAt;
    @Column(name="movement_confirmed_at") private Instant movingAt;
    @Column(name="stationary_since") private Instant stationarySince;
    @Column(name="inactivity_prompted_at") private Instant promptedAt;
    @Column(name="location_anchor_lat") private Double anchorLat;
    @Column(name="location_anchor_lon") private Double anchorLon;
    @Column(name="location_anchor_accuracy") private Double anchorAccuracy;
    @Column(name="location_anchor_at") private Instant anchorAt;
    @Column(name="location_last_lat") private Double lastLat;
    @Column(name="location_last_lon") private Double lastLon;
    @Column(name="location_last_accuracy") private Double lastAccuracy;
    @Column(name="movement_waiting") private Boolean waiting;
    @Column(name="location_uncertain") private Boolean uncertain;

    public void start(Instant now) { if (startedAt == null) { startedAt = now; uncertain = true; } }
    public boolean enabled() { return startedAt != null; }
    public boolean fresh(Instant now) {
        return enabled() && !Boolean.TRUE.equals(uncertain) && reliableAt != null
                && !now.isBefore(reliableAt) && now.isBefore(reliableAt.plusSeconds(FRESH_SECONDS));
    }
    public Instant evidenceDueAt() {
        return reliableAt == null ? startedAt : reliableAt.plusSeconds(FRESH_SECONDS);
    }
    public void loseSignal() { uncertain = true; resetContinuity(); }
    private void resetContinuity() {
        stationarySince = null; promptedAt = null; anchorLat = null; anchorLon = null;
        anchorAccuracy = null; anchorAt = null; waiting = false;
    }
    private void anchor(LocationSample p, Instant now) {
        anchorLat=p.latitude(); anchorLon=p.longitude(); anchorAccuracy=p.accuracy(); anchorAt=now;
    }
    /** Returns true only for plausible displacement on the saved route, beyond both accuracy circles. */
    public boolean accept(LocationSample p, ShuttleRoute route, Instant now) {
        p.validate(now);
        if (!enabled()) throw new IllegalStateException("Enable location monitoring for this session first.");
        if (capturedAt != null && !p.capturedAt().isAfter(capturedAt)) return false;
        boolean continuous = fresh(now) && capturedAt != null
                && !p.capturedAt().isAfter(capturedAt.plusSeconds(FRESH_SECONDS));
        double elapsed = capturedAt == null ? 0 : Duration.between(capturedAt,p.capturedAt()).toMillis()/1000.0;
        boolean jump = continuous && lastLat != null && elapsed > 0
                && Math.max(0, ShuttleRoute.distance(lastLat,lastLon,p.latitude(),p.longitude())
                        - lastAccuracy-p.accuracy()) / elapsed > 35;
        capturedAt=p.capturedAt(); receivedAt=now;
        if (p.accuracy()>35 || (p.speed()!=null && p.speed()>35) || jump
                || (route.configured() && !route.contains(p.latitude(),p.longitude(),p.accuracy()))) {
            loseSignal(); return false;
        }
        if (!continuous) resetContinuity();
        reliableAt=now; uncertain=false;
        lastLat=p.latitude(); lastLon=p.longitude(); lastAccuracy=p.accuracy();
        if (anchorLat == null) { anchor(p,now); stationarySince=now; }
        double distance=ShuttleRoute.distance(anchorLat,anchorLon,p.latitude(),p.longitude());
        double threshold=Math.max(40,anchorAccuracy+p.accuracy()+10);
        boolean displacement=distance>threshold;
        boolean speedSuggestsMotion=p.speed()!=null && p.speed()>1.5;
        boolean confirmed=displacement && Duration.between(anchorAt,now).toSeconds()>=10 && route.configured();
        if (displacement || speedSuggestsMotion) {
            stationarySince=null; promptedAt=null; waiting=false;
            if (displacement) anchor(p,now);
            if (confirmed) movingAt=now;
        } else if (stationarySince==null) {
            stationarySince=now;
            anchor(p,now);
        }
        return confirmed;
    }
    public void evaluate(Instant now) {
        if (!fresh(now)) { if (enabled()) loseSignal(); return; }
        if (stationarySince!=null && !now.isBefore(stationarySince.plusSeconds(PROMPT_SECONDS)) && promptedAt==null)
            promptedAt=now;
    }
    public boolean timedOut(Instant now) {
        return fresh(now) && stationarySince!=null && promptedAt!=null
                && !now.isBefore(stationarySince.plusSeconds(TIMEOUT_SECONDS))
                && !now.isBefore(promptedAt.plusSeconds(TIMEOUT_SECONDS-PROMPT_SECONDS));
    }
    public void acknowledge(Instant now, boolean waitingForPassengers) {
        promptedAt=null; waiting=waitingForPassengers;
        // A human response restarts the observation period, never makes a missing GPS feed fresh.
        stationarySince=fresh(now) ? now : null;
        if (lastLat!=null) { anchorLat=lastLat; anchorLon=lastLon; anchorAccuracy=lastAccuracy; anchorAt=now; }
    }
    public boolean waiting() { return Boolean.TRUE.equals(waiting); }
    public Instant getStartedAt() { return startedAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getReliableAt() { return reliableAt; }
    public Instant getMovingAt() { return movingAt; }
    public Instant getStationarySince() { return stationarySince; }
    public Instant getPromptedAt() { return promptedAt; }
    public Instant timeoutAt() {
        if (promptedAt==null || stationarySince==null) return null;
        Instant nominal=stationarySince.plusSeconds(TIMEOUT_SECONDS), reply=promptedAt.plusSeconds(PROMPT_SECONDS);
        return nominal.isAfter(reply) ? nominal : reply;
    }
    public void clearCoordinates() {
        anchorLat=null; anchorLon=null; anchorAccuracy=null; anchorAt=null;
        lastLat=null; lastLon=null; lastAccuracy=null;
    }
}
