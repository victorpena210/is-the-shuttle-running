package technology.renatus.routepulse.scheduling.planning.pickup;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import technology.renatus.routepulse.scheduling.domain.PickupWindow;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.planning.common.DemandPriorityClassifier;

/** Converts shift end-times into manager-facing 15-minute pickup windows. */
public class PickupWindowCalculator {

    public static final int WINDOW_MINUTES = 15;

    private final DemandPriorityClassifier priorityClassifier;

    public PickupWindowCalculator(DemandPriorityClassifier priorityClassifier) {
        this.priorityClassifier = priorityClassifier;
    }

    public List<PickupWindow> createWindows(List<ShiftRecord> shifts) {
        Map<LocalTime, Long> shiftCounts = shifts.stream()
                .collect(Collectors.groupingBy(
                        shift -> calculateWindowStart(shift.getEndTime()),
                        Collectors.counting()
                ));

        return shiftCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> createPickupWindow(entry.getKey(), entry.getValue()))
                .toList();
    }

    private PickupWindow createPickupWindow(LocalTime windowStart, long teamMemberCount) {
        int count = Math.toIntExact(teamMemberCount);
        return new PickupWindow(
                windowStart,
                windowStart.plusMinutes(WINDOW_MINUTES),
                count,
                priorityClassifier.classify(count)
        );
    }

    private LocalTime calculateWindowStart(LocalTime endTime) {
        int roundedMinute = (endTime.getMinute() / WINDOW_MINUTES) * WINDOW_MINUTES;
        return endTime.withMinute(roundedMinute).withSecond(0).withNano(0);
    }
}
