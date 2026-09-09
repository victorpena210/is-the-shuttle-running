package technology.renatus.routepulse.assignments.service.planning;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;

/** Chooses the most appropriate uploaded work-schedule row for a driver assignment. */
public class DriverScheduleSelector {

    public DriverSchedule chooseMorning(
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            MorningDrivingPlan morningPlan) {
        if (fairestOrder == null || fairestOrder.isEmpty()) {
            return null;
        }

        // Preserve previous behavior when there is no generated morning-demand window.
        if (morningPlan == null || !morningPlan.hasDemand()) {
            return fairestOrder.get(0);
        }

        for (DriverSchedule rankedDriver : fairestOrder) {
            DriverSchedule overlappingSchedule = findBestOverlapping(
                    schedulesByDriverId.getOrDefault(rankedDriver.driverId(), List.of()),
                    rankedDriver.driverId(),
                    morningPlan.getScheduledStartTime(),
                    morningPlan.getScheduledEndTime()
            );
            if (overlappingSchedule != null) {
                return overlappingSchedule;
            }
        }
        return null;
    }

    public DriverSchedule chooseAfternoon(
            List<DriverSchedule> fairestOrder,
            DriverSchedule morningDriverSchedule) {
        if (fairestOrder == null || fairestOrder.size() < 2) {
            return null;
        }
        if (morningDriverSchedule == null) {
            return fairestOrder.get(1);
        }

        return fairestOrder.stream()
                .filter(schedule -> !schedule.driverId().equals(morningDriverSchedule.driverId()))
                .findFirst()
                .orElse(null);
    }

    public DriverSchedule findBestOverlapping(
            List<DriverSchedule> schedules,
            Long driverId,
            LocalTime requestedStart,
            LocalTime requestedEnd) {
        if (schedules == null || schedules.isEmpty() || driverId == null) {
            return null;
        }

        return schedules.stream()
                .filter(schedule -> driverId.equals(schedule.driverId())
                        && schedule.overlaps(requestedStart, requestedEnd))
                .sorted(
                        Comparator.comparingLong(
                                        (DriverSchedule schedule) -> schedule.overlapMinutes(
                                                requestedStart,
                                                requestedEnd
                                        )
                                )
                                .reversed()
                                .thenComparing(DriverSchedule::startTime)
                )
                .findFirst()
                .orElse(null);
    }

    public DriverSchedule earliest(List<DriverSchedule> schedules) {
        return schedules.stream()
                .min(Comparator.comparing(DriverSchedule::startTime)
                        .thenComparing(DriverSchedule::endTime))
                .orElseThrow(() -> new IllegalStateException(
                        "A scheduled driver must have at least one shift row."
                ));
    }
}
