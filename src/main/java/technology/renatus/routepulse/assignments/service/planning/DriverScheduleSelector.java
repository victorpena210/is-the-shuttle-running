package technology.renatus.routepulse.assignments.service.planning;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.MorningDrivingPlan;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

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

        return chooseForWindow(fairestOrder, schedulesByDriverId,
                morningPlan.getScheduledStartTime(), morningPlan.getScheduledEndTime(), Set.of());
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

    /**
     * Chooses the fairest driver schedule that overlaps a requested driving
     * window. Drivers in avoidDriverIds are skipped when another eligible
     * driver can cover the window, then considered as a fallback so a small
     * team can still cover more than one period in the same day.
     */
    public DriverSchedule chooseForWindow(
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            LocalTime requestedStart,
            LocalTime requestedEnd,
            Set<Long> avoidDriverIds) {

        if (fairestOrder == null
                || fairestOrder.isEmpty()
                || requestedStart == null
                || requestedEnd == null) {
            return null;
        }

        Set<Long> avoided = avoidDriverIds == null ? Set.of() : avoidDriverIds;

        // Do not shorten coverage merely to select a driver with fewer hours.
        for (boolean skipAvoided : List.of(true, false)) {
            for (DriverSchedule driver : fairestOrder) {
                if (skipAvoided && avoided.contains(driver.driverId())) continue;
                DriverSchedule covering = schedulesByDriverId.getOrDefault(driver.driverId(), List.of()).stream()
                        .filter(schedule -> schedule.covers(requestedStart, requestedEnd))
                        .min(Comparator.comparing(DriverSchedule::startTime)).orElse(null);
                if (covering != null) return covering;
            }
        }

        DriverSchedule preferred = findFirstOverlapping(
                fairestOrder,
                schedulesByDriverId,
                requestedStart,
                requestedEnd,
                avoided,
                true
        );
        if (preferred != null) {
            return preferred;
        }

        return findFirstOverlapping(
                fairestOrder,
                schedulesByDriverId,
                requestedStart,
                requestedEnd,
                avoided,
                false
        );
    }

    private DriverSchedule findFirstOverlapping(
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            LocalTime requestedStart,
            LocalTime requestedEnd,
            Set<Long> avoided,
            boolean skipAvoided) {

        for (DriverSchedule rankedDriver : fairestOrder) {
            if (skipAvoided && avoided.contains(rankedDriver.driverId())) {
                continue;
            }

            DriverSchedule overlapping = findBestOverlapping(
                    schedulesByDriverId.getOrDefault(rankedDriver.driverId(), List.of()),
                    rankedDriver.driverId(),
                    requestedStart,
                    requestedEnd
            );
            if (overlapping != null) {
                return overlapping;
            }
        }
        return null;
    }

    /**
     * Midday selection is based on the actual sporadic demand windows instead
     * of the broad first-to-last span. This prevents a driver who is only
     * working during a quiet midday gap from being selected over a driver who
     * can cover the real turnover windows.
     */
    public DriverSchedule chooseForDemandWindows(
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            List<TransportationDemandWindow> demandWindows,
            Set<Long> avoidDriverIds) {

        if (fairestOrder == null
                || fairestOrder.isEmpty()
                || demandWindows == null
                || demandWindows.isEmpty()) {
            return null;
        }

        Set<Long> avoided = avoidDriverIds == null ? Set.of() : avoidDriverIds;
        return findFirstDemandOverlapping(
                fairestOrder,
                schedulesByDriverId,
                demandWindows,
                avoided,
                true
        );
    }

    private DriverSchedule findFirstDemandOverlapping(
            List<DriverSchedule> fairestOrder,
            Map<Long, List<DriverSchedule>> schedulesByDriverId,
            List<TransportationDemandWindow> demandWindows,
            Set<Long> avoided,
            boolean skipAvoided) {

        List<TransportationDemandWindow> activeDemandWindows = demandWindows.stream()
                .filter(window -> window != null && window.getDemandCount() > 0)
                .toList();
        if (activeDemandWindows.isEmpty()) {
            return null;
        }

        long totalDemandMinutes = activeDemandWindows.stream()
                .mapToLong(window -> java.time.Duration.between(
                        window.getWindowStart(),
                        window.getWindowEnd()
                ).toMinutes())
                .sum();

        DemandCoverage bestPartial = null;

        for (int fairnessRank = 0; fairnessRank < fairestOrder.size(); fairnessRank++) {
            DriverSchedule rankedDriver = fairestOrder.get(fairnessRank);
            if (skipAvoided && avoided.contains(rankedDriver.driverId())) {
                continue;
            }

            List<DriverSchedule> driverRows = schedulesByDriverId
                    .getOrDefault(rankedDriver.driverId(), List.of());
            if (driverRows.isEmpty()) {
                continue;
            }

            long coverageMinutes = activeDemandWindows.stream()
                    .mapToLong(window -> Math.min(
                            java.time.Duration.between(
                                    window.getWindowStart(),
                                    window.getWindowEnd()
                            ).toMinutes(),
                            driverRows.stream()
                                    .mapToLong(schedule -> schedule.overlapMinutes(
                                            window.getWindowStart(),
                                            window.getWindowEnd()))
                                    .sum()
                    ))
                    .sum();

            if (coverageMinutes <= 0) {
                continue;
            }

            DriverSchedule representative = earliest(driverRows);

            // Fairness wins immediately among drivers who can cover every
            // real midday demand minute. If nobody can, retain the driver who
            // covers the most demand, using fairness as the tie-breaker.
            if (coverageMinutes >= totalDemandMinutes) {
                return representative;
            }

            DemandCoverage candidate = new DemandCoverage(
                    representative,
                    coverageMinutes,
                    fairnessRank
            );
            if (bestPartial == null
                    || candidate.minutes() > bestPartial.minutes()
                    || (candidate.minutes() == bestPartial.minutes()
                            && candidate.fairnessRank() < bestPartial.fairnessRank())) {
                bestPartial = candidate;
            }
        }

        return bestPartial == null ? null : bestPartial.schedule();
    }

    private record DemandCoverage(
            DriverSchedule schedule,
            long minutes,
            int fairnessRank) {
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
