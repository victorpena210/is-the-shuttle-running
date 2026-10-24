package technology.renatus.routepulse.scheduling.planning.transportation;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;

/** Places shift starts/ends into operating-day demand buckets. */
public class TransportationDemandBucketizer {

    private final LocalTime operatingStart;
    private final LocalTime middayStart;
    private final LocalTime eveningStart;
    private final LocalTime operatingEnd;
    private final int windowMinutes;

    public TransportationDemandBucketizer(
            LocalTime operatingStart,
            LocalTime middayStart,
            LocalTime eveningStart,
            LocalTime operatingEnd,
            int windowMinutes) {
        if (windowMinutes <= 0 || 60 % windowMinutes != 0) {
            throw new IllegalArgumentException(
                    "Transportation-demand window minutes must be a positive divisor of 60."
            );
        }
        if (!operatingStart.isBefore(middayStart)
                || !middayStart.isBefore(eveningStart)
                || !eveningStart.isBefore(operatingEnd)) {
            throw new IllegalArgumentException(
                    "Transportation-demand day boundaries must be ordered: "
                            + "operating start < midday start < evening start < operating end."
            );
        }

        this.operatingStart = operatingStart;
        this.middayStart = middayStart;
        this.eveningStart = eveningStart;
        this.operatingEnd = operatingEnd;
        this.windowMinutes = windowMinutes;
    }

    public Buckets bucket(List<ShiftRecord> shifts) {
        Buckets buckets = new Buckets();
        for (ShiftRecord shift : shifts) {
            addEvent(shift.getStartTime(), true, buckets);
            addEvent(shift.getEndTime(), false, buckets);
        }
        return buckets;
    }

    private void addEvent(LocalTime eventTime, boolean arrival, Buckets buckets) {
        if (eventTime == null) {
            return;
        }

        Map<LocalTime, WindowCounts> target = selectBucket(eventTime, arrival, buckets);
        LocalTime windowStart = floorToWindow(eventTime);
        WindowCounts counts = target.computeIfAbsent(windowStart, ignored -> new WindowCounts());
        if (arrival) {
            counts.arrivals++;
        } else {
            counts.departures++;
        }
    }

    private Map<LocalTime, WindowCounts> selectBucket(
            LocalTime eventTime,
            boolean arrival,
            Buckets buckets) {
        return buckets.forType(periodFor(eventTime, arrival));
    }

    /** Shared with the upload forecast so the established period boundaries stay consistent. */
    public TransportationPeriodType periodFor(LocalTime eventTime, boolean arrival) {
        // Exactly operatingEnd remains normal operations; anything after it is off-hours.
        if (eventTime.isBefore(operatingStart) || eventTime.isAfter(operatingEnd)) {
            return TransportationPeriodType.OFF_HOURS;
        }

        // Noon arrivals stay aligned with MorningDrivingPlanningService; noon departures are midday.
        if (eventTime.isBefore(middayStart) || (arrival && eventTime.equals(middayStart))) {
            return TransportationPeriodType.MORNING;
        }
        if (eventTime.isBefore(eveningStart)) {
            return TransportationPeriodType.MIDDAY;
        }
        return TransportationPeriodType.EVENING;
    }

    private LocalTime floorToWindow(LocalTime time) {
        int roundedMinute = (time.getMinute() / windowMinutes) * windowMinutes;
        return time.withMinute(roundedMinute).withSecond(0).withNano(0);
    }

    public static final class Buckets {
        private final Map<LocalTime, WindowCounts> morning = new TreeMap<>();
        private final Map<LocalTime, WindowCounts> midday = new TreeMap<>();
        private final Map<LocalTime, WindowCounts> evening = new TreeMap<>();
        private final Map<LocalTime, WindowCounts> offHours = new TreeMap<>();

        public Map<LocalTime, WindowCounts> forType(TransportationPeriodType type) {
            return switch (type) {
                case MORNING -> morning;
                case MIDDAY -> midday;
                case EVENING -> evening;
                case OFF_HOURS -> offHours;
            };
        }
    }

    public static final class WindowCounts {
        private int arrivals;
        private int departures;

        public int arrivals() { return arrivals; }
        public int departures() { return departures; }
    }
}
