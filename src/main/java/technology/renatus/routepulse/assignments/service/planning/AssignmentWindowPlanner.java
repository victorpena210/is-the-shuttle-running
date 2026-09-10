package technology.renatus.routepulse.assignments.service.planning;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

/**
 * Converts transportation demand into the detailed start/stop windows a driver
 * should actually follow.
 *
 * Morning/afternoon remain mostly continuous. If there is a demand-free gap of
 * at least the configured break length, one protected break is carved out.
 * Midday is intentionally different: only demand-bearing runs are scheduled,
 * so the driver receives explicit start/stop intervals instead of one broad
 * block of driving time.
 */
public class AssignmentWindowPlanner {

    public boolean applyContinuousPlanWithBreak(
            DriverAssignment assignment,
            DriverSchedule driverSchedule,
            LocalTime requestedStart,
            LocalTime requestedEnd,
            List<TransportationDemandWindow> demandWindows,
            int breakMinutes) {

        if (assignment == null || driverSchedule == null) {
            return false;
        }

        DriverSchedule.DrivingWindow clipped = driverSchedule.clipTo(
                requestedStart,
                requestedEnd
        );
        if (clipped == null) {
            return false;
        }

        assignment.updateScheduledTimes(clipped.startTime(), clipped.endTime());

        PlannedDrivingWindow breakWindow = findBestBreakWindow(
                clipped.startTime(),
                clipped.endTime(),
                demandWindows,
                breakMinutes
        );

        List<PlannedDrivingWindow> planned = new ArrayList<>();
        if (breakWindow == null) {
            planned.add(new PlannedDrivingWindow(clipped.startTime(), clipped.endTime()));
        } else {
            if (breakWindow.getStartTime().isAfter(clipped.startTime())) {
                planned.add(new PlannedDrivingWindow(
                        clipped.startTime(),
                        breakWindow.getStartTime()
                ));
            }
            if (clipped.endTime().isAfter(breakWindow.getEndTime())) {
                planned.add(new PlannedDrivingWindow(
                        breakWindow.getEndTime(),
                        clipped.endTime()
                ));
            }
        }

        assignment.replacePlannedDrivingWindows(planned);
        return true;
    }

    public boolean applyMiddayPlan(
            DriverAssignment assignment,
            DriverSchedule driverSchedule,
            List<TransportationDemandWindow> demandWindows) {

        return applyMiddayPlan(
                assignment,
                driverSchedule == null ? List.of() : List.of(driverSchedule),
                demandWindows
        );
    }

    /**
     * Plans midday against all uploaded work rows for the selected driver. This
     * matters for split shifts: quiet/off-the-clock spans stay out of the
     * driving plan while later eligible demand windows can still be included.
     */
    public boolean applyMiddayPlan(
            DriverAssignment assignment,
            List<DriverSchedule> driverSchedules,
            List<TransportationDemandWindow> demandWindows) {

        if (assignment == null
                || driverSchedules == null
                || driverSchedules.isEmpty()
                || demandWindows == null
                || demandWindows.isEmpty()) {
            return false;
        }

        List<PlannedDrivingWindow> mergedDemandRuns = mergeContiguousDemandWindows(demandWindows);
        List<PlannedDrivingWindow> clippedRuns = new ArrayList<>();

        for (PlannedDrivingWindow run : mergedDemandRuns) {
            for (DriverSchedule driverSchedule : driverSchedules) {
                DriverSchedule.DrivingWindow clipped = driverSchedule.clipTo(
                        run.getStartTime(),
                        run.getEndTime()
                );
                if (clipped != null) {
                    clippedRuns.add(new PlannedDrivingWindow(
                            clipped.startTime(),
                            clipped.endTime()
                    ));
                }
            }
        }

        List<PlannedDrivingWindow> normalizedRuns = mergePlannedWindows(clippedRuns);
        if (normalizedRuns.isEmpty()) {
            return false;
        }

        LocalTime broadStart = normalizedRuns.get(0).getStartTime();
        LocalTime broadEnd = normalizedRuns.get(normalizedRuns.size() - 1).getEndTime();
        assignment.updateScheduledTimes(broadStart, broadEnd);
        assignment.replacePlannedDrivingWindows(normalizedRuns);
        return true;
    }

    public List<PlannedDrivingWindow> mergeContiguousDemandWindows(
            List<TransportationDemandWindow> demandWindows) {

        if (demandWindows == null || demandWindows.isEmpty()) {
            return List.of();
        }

        List<TransportationDemandWindow> sorted = demandWindows.stream()
                .filter(window -> window != null && window.getDemandCount() > 0)
                .sorted(Comparator.comparing(TransportationDemandWindow::getWindowStart))
                .toList();

        if (sorted.isEmpty()) {
            return List.of();
        }

        List<PlannedDrivingWindow> merged = new ArrayList<>();
        LocalTime runStart = sorted.get(0).getWindowStart();
        LocalTime runEnd = sorted.get(0).getWindowEnd();

        for (int index = 1; index < sorted.size(); index++) {
            TransportationDemandWindow window = sorted.get(index);

            if (!window.getWindowStart().isAfter(runEnd)) {
                if (window.getWindowEnd().isAfter(runEnd)) {
                    runEnd = window.getWindowEnd();
                }
                continue;
            }

            merged.add(new PlannedDrivingWindow(runStart, runEnd));
            runStart = window.getWindowStart();
            runEnd = window.getWindowEnd();
        }

        merged.add(new PlannedDrivingWindow(runStart, runEnd));
        return List.copyOf(merged);
    }

    private List<PlannedDrivingWindow> mergePlannedWindows(
            List<PlannedDrivingWindow> windows) {

        if (windows == null || windows.isEmpty()) {
            return List.of();
        }

        List<PlannedDrivingWindow> sorted = windows.stream()
                .sorted(Comparator.comparing(PlannedDrivingWindow::getStartTime))
                .toList();

        List<PlannedDrivingWindow> merged = new ArrayList<>();
        LocalTime runStart = sorted.get(0).getStartTime();
        LocalTime runEnd = sorted.get(0).getEndTime();

        for (int index = 1; index < sorted.size(); index++) {
            PlannedDrivingWindow window = sorted.get(index);
            if (!window.getStartTime().isAfter(runEnd)) {
                if (window.getEndTime().isAfter(runEnd)) {
                    runEnd = window.getEndTime();
                }
                continue;
            }

            merged.add(new PlannedDrivingWindow(runStart, runEnd));
            runStart = window.getStartTime();
            runEnd = window.getEndTime();
        }

        merged.add(new PlannedDrivingWindow(runStart, runEnd));
        return List.copyOf(merged);
    }

    private PlannedDrivingWindow findBestBreakWindow(
            LocalTime assignmentStart,
            LocalTime assignmentEnd,
            List<TransportationDemandWindow> demandWindows,
            int breakMinutes) {

        if (breakMinutes <= 0
                || demandWindows == null
                || demandWindows.size() < 2) {
            return null;
        }

        List<TransportationDemandWindow> relevant = demandWindows.stream()
                .filter(window -> window != null && window.getDemandCount() > 0)
                .filter(window -> window.getWindowEnd().isAfter(assignmentStart))
                .filter(window -> window.getWindowStart().isBefore(assignmentEnd))
                .sorted(Comparator.comparing(TransportationDemandWindow::getWindowStart))
                .toList();

        if (relevant.size() < 2) {
            return null;
        }

        long assignmentMinutes = Duration.between(assignmentStart, assignmentEnd).toMinutes();
        LocalTime midpoint = assignmentStart.plusMinutes(Math.max(0, assignmentMinutes / 2));
        List<BreakCandidate> candidates = new ArrayList<>();

        for (int index = 0; index < relevant.size() - 1; index++) {
            LocalTime gapStart = laterOf(relevant.get(index).getWindowEnd(), assignmentStart);
            LocalTime gapEnd = earlierOf(relevant.get(index + 1).getWindowStart(), assignmentEnd);

            if (!gapEnd.isAfter(gapStart)) {
                continue;
            }

            long gapMinutes = Duration.between(gapStart, gapEnd).toMinutes();
            if (gapMinutes < breakMinutes) {
                continue;
            }

            LocalTime breakEnd = gapStart.plusMinutes(breakMinutes);
            long distanceFromMidpoint = Math.abs(Duration.between(midpoint, gapStart).toMinutes());
            candidates.add(new BreakCandidate(
                    new PlannedDrivingWindow(gapStart, breakEnd),
                    distanceFromMidpoint
            ));
        }

        return candidates.stream()
                .min(Comparator.comparingLong(BreakCandidate::distanceFromMidpoint))
                .map(BreakCandidate::window)
                .orElse(null);
    }

    private LocalTime laterOf(LocalTime first, LocalTime second) {
        return first.isAfter(second) ? first : second;
    }

    private LocalTime earlierOf(LocalTime first, LocalTime second) {
        return first.isBefore(second) ? first : second;
    }

    private record BreakCandidate(
            PlannedDrivingWindow window,
            long distanceFromMidpoint) {
    }
}
