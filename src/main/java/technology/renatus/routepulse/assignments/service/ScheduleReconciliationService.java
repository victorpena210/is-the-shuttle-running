package technology.renatus.routepulse.assignments.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.assignments.domain.AssignmentSource;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.model.PlannedDrivingWindow;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliation;
import technology.renatus.routepulse.assignments.model.ScheduleReconciliationStatus;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.model.UploadedShift;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/**
 * Compares the schedule version an automatic assignment was based on with the
 * newest schedule import. New schedule data becomes authoritative immediately,
 * while already-created assignments remain untouched until a manager resolves a
 * meaningful conflict.
 */
@Service
public class ScheduleReconciliationService {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private final UploadedShiftRepository uploadedShiftRepository;
    private final DriverWorkloadService driverWorkloadService;

    public ScheduleReconciliationService(
            UploadedShiftRepository uploadedShiftRepository,
            DriverWorkloadService driverWorkloadService) {
        this.uploadedShiftRepository = uploadedShiftRepository;
        this.driverWorkloadService = driverWorkloadService;
    }

    @Transactional(readOnly = true)
    public List<ScheduleReconciliation> reconcile(
            Long organizationId,
            List<DriverAssignment> assignments,
            List<DriverSchedule> currentDriverSchedules,
            TransportationDemandAnalysis transportationDemand,
            ScheduleImport latestScheduleImport) {

        if (latestScheduleImport == null
                || assignments == null
                || assignments.isEmpty()) {
            return List.of();
        }

        List<DriverSchedule> schedules = currentDriverSchedules == null
                ? List.of()
                : currentDriverSchedules;

        List<ScheduleReconciliation> results = new ArrayList<>();

        for (DriverAssignment assignment : assignments) {
            ScheduleReconciliation reconciliation = reconcileAssignment(
                    organizationId,
                    assignment,
                    schedules,
                    transportationDemand,
                    latestScheduleImport
            );

            if (reconciliation != null) {
                results.add(reconciliation);
            }
        }

        return results.stream()
                .sorted(Comparator.comparingInt(item -> item.period().ordinal()))
                .toList();
    }

    private ScheduleReconciliation reconcileAssignment(
            Long organizationId,
            DriverAssignment assignment,
            List<DriverSchedule> currentDriverSchedules,
            TransportationDemandAnalysis transportationDemand,
            ScheduleImport latestScheduleImport) {

        if (assignment == null
                || assignment.getAssignmentSource() == AssignmentSource.MANUAL
                || assignment.getScheduledDriver() == null) {
            return null;
        }

        ScheduleImport baselineImport = assignment.getReviewedScheduleImport() == null
                ? assignment.getSourceScheduleImport()
                : assignment.getReviewedScheduleImport();

        if (baselineImport != null
                && Objects.equals(baselineImport.getId(), latestScheduleImport.getId())) {
            return null;
        }

        String employeeId = assignment.getScheduledDriver().getEmployeeId();
        List<UploadedShift> previousShifts = baselineImport == null
                ? List.of()
                : uploadedShiftRepository
                        .findAllByOrganization_IdAndScheduleImport_IdAndShiftDateAndEmployeeIdOrderByStartTimeAsc(
                                organizationId,
                                baselineImport.getId(),
                                assignment.getAssignmentDate(),
                                employeeId
                        );

        List<UploadedShift> latestShifts = uploadedShiftRepository
                .findAllByOrganization_IdAndScheduleImport_IdAndShiftDateAndEmployeeIdOrderByStartTimeAsc(
                        organizationId,
                        latestScheduleImport.getId(),
                        assignment.getAssignmentDate(),
                        employeeId
                );

        // A same-week re-upload with identical hours is not a meaningful change.
        if (baselineImport != null && sameShiftWindows(previousShifts, latestShifts)) {
            return null;
        }

        LocalTime requestedStart = effectiveStart(assignment, transportationDemand);
        LocalTime requestedEnd = effectiveEnd(assignment, transportationDemand);
        String drivingWindowLabel = assignment.hasPlannedDrivingWindows()
                ? formatPlannedWindows(assignment)
                : formatWindow(requestedStart, requestedEnd);
        String previousScheduleLabel = baselineImport == null
                ? "Previous imported shift unavailable"
                : formatShifts(previousShifts);
        String latestScheduleLabel = formatShifts(latestShifts);
        String driverName = assignment.getScheduledDriver().getName();
        String periodLabel = assignment.getPeriodLabel();

        if (assignment.hasDrivingSessions()) {
            return new ScheduleReconciliation(
                    assignment.getId(),
                    assignment.getPeriod(),
                    periodLabel,
                    assignment.getScheduledDriver().getId(),
                    driverName,
                    baselineImport == null ? null : baselineImport.getId(),
                    latestScheduleImport.getId(),
                    previousScheduleLabel,
                    latestScheduleLabel,
                    drivingWindowLabel,
                    ScheduleReconciliationStatus.HISTORY_PRESERVED,
                    null,
                    null,
                    null,
                    "The latest upload changed " + driverName
                            + "'s work schedule after driving activity was already recorded. "
                            + "Is the Shuttle Running preserved the original assignment and actual driving history."
            );
        }

        boolean stillValid = latestShiftsCover(
                latestShifts,
                assignment,
                requestedStart,
                requestedEnd
        );

        if (stillValid) {
            String detail = drivingWindowLabel == null
                    ? driverName + " still has a usable work shift in the latest schedule."
                    : driverName + "'s work shift changed, but the latest schedule still covers "
                            + drivingWindowLabel + " for " + periodLabel + " driving.";

            return new ScheduleReconciliation(
                    assignment.getId(),
                    assignment.getPeriod(),
                    periodLabel,
                    assignment.getScheduledDriver().getId(),
                    driverName,
                    baselineImport == null ? null : baselineImport.getId(),
                    latestScheduleImport.getId(),
                    previousScheduleLabel,
                    latestScheduleLabel,
                    drivingWindowLabel,
                    ScheduleReconciliationStatus.COVERAGE_STILL_VALID,
                    null,
                    null,
                    null,
                    detail
            );
        }

        DriverSchedule recommended = findRecommendedReplacement(
                organizationId,
                assignment,
                currentDriverSchedules,
                requestedStart,
                requestedEnd
        );

        if (recommended != null) {
            String detail = drivingWindowLabel == null
                    ? driverName + " no longer has a usable work shift in the latest schedule. "
                            + "Is the Shuttle Running recommends " + recommended.driverName() + "."
                    : driverName + " no longer covers " + drivingWindowLabel + ". "
                            + "Is the Shuttle Running recommends " + recommended.driverName()
                            + ", whose latest shift covers the required driving time.";

            return new ScheduleReconciliation(
                    assignment.getId(),
                    assignment.getPeriod(),
                    periodLabel,
                    assignment.getScheduledDriver().getId(),
                    driverName,
                    baselineImport == null ? null : baselineImport.getId(),
                    latestScheduleImport.getId(),
                    previousScheduleLabel,
                    latestScheduleLabel,
                    drivingWindowLabel,
                    ScheduleReconciliationStatus.REPLACEMENT_RECOMMENDED,
                    recommended.driverId(),
                    recommended.driverName(),
                    formatDriverSchedules(currentDriverSchedules, recommended.driverId()),
                    detail
            );
        }

        String detail = drivingWindowLabel == null
                ? driverName + " no longer has a usable work shift in the latest schedule, and no other scheduled driver is available."
                : driverName + " no longer covers " + drivingWindowLabel
                        + ", and no other scheduled Is the Shuttle Running driver covers the required driving time.";

        return new ScheduleReconciliation(
                assignment.getId(),
                assignment.getPeriod(),
                periodLabel,
                assignment.getScheduledDriver().getId(),
                driverName,
                baselineImport == null ? null : baselineImport.getId(),
                latestScheduleImport.getId(),
                previousScheduleLabel,
                latestScheduleLabel,
                drivingWindowLabel,
                ScheduleReconciliationStatus.NO_REPLACEMENT_AVAILABLE,
                null,
                null,
                null,
                detail
        );
    }

    private DriverSchedule findRecommendedReplacement(
            Long organizationId,
            DriverAssignment assignment,
            List<DriverSchedule> currentDriverSchedules,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (currentDriverSchedules == null || currentDriverSchedules.isEmpty()) {
            return null;
        }

        Map<Long, List<DriverSchedule>> byDriver = currentDriverSchedules.stream()
                .filter(schedule -> !schedule.driverId().equals(
                        assignment.getScheduledDriver().getId()
                ))
                .collect(Collectors.groupingBy(DriverSchedule::driverId));

        List<DriverSchedule> candidates = byDriver.values().stream()
                .map(schedules -> bestCoveringSchedule(
                        schedules,
                        assignment,
                        requestedStart,
                        requestedEnd
                ))
                .filter(Objects::nonNull)
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        return driverWorkloadService
                .rankEligibleDrivers(
                        organizationId,
                        assignment.getAssignmentDate(),
                        candidates
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private DriverSchedule bestCoveringSchedule(
            List<DriverSchedule> schedules,
            DriverAssignment assignment,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (schedules == null || schedules.isEmpty()) {
            return null;
        }

        if (!driverSchedulesCover(schedules, assignment, requestedStart, requestedEnd)) {
            return null;
        }

        return schedules.stream()
                .min(Comparator.comparing(DriverSchedule::startTime))
                .orElse(null);
    }

    private boolean driverSchedulesCover(
            List<DriverSchedule> schedules,
            DriverAssignment assignment,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (schedules == null || schedules.isEmpty()) {
            return false;
        }

        if (assignment.hasPlannedDrivingWindows()) {
            return assignment.getPlannedDrivingWindows().stream()
                    .allMatch(window -> schedules.stream().anyMatch(schedule -> schedule.covers(
                            window.getStartTime(),
                            window.getEndTime()
                    )));
        }

        if (requestedStart == null || requestedEnd == null) {
            return true;
        }

        return schedules.stream().anyMatch(schedule -> schedule.covers(
                requestedStart,
                requestedEnd
        ));
    }

    private boolean latestShiftsCover(
            List<UploadedShift> shifts,
            DriverAssignment assignment,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (shifts == null || shifts.isEmpty()) {
            return false;
        }

        if (assignment.hasPlannedDrivingWindows()) {
            return assignment.getPlannedDrivingWindows().stream()
                    .allMatch(window -> shifts.stream().anyMatch(shift -> coversWindow(
                            shift.getStartTime(),
                            shift.getEndTime(),
                            window.getStartTime(),
                            window.getEndTime()
                    )));
        }

        if (requestedStart == null || requestedEnd == null) {
            return true;
        }

        return shifts.stream().anyMatch(shift -> coversWindow(
                shift.getStartTime(),
                shift.getEndTime(),
                requestedStart,
                requestedEnd
        ));
    }

    private boolean coversWindow(
            LocalTime shiftStart,
            LocalTime shiftEnd,
            LocalTime requestedStart,
            LocalTime requestedEnd) {

        if (shiftStart == null || shiftEnd == null
                || requestedStart == null || requestedEnd == null
                || shiftStart.equals(shiftEnd)
                || requestedStart.equals(requestedEnd)) {
            return false;
        }

        int requestStartMinute = toMinute(requestedStart);
        int requestEndMinute = normalizeEnd(
                requestStartMinute,
                toMinute(requestedEnd)
        );
        int shiftStartMinute = toMinute(shiftStart);
        int shiftEndMinute = normalizeEnd(
                shiftStartMinute,
                toMinute(shiftEnd)
        );

        return shiftStartMinute <= requestStartMinute
                && shiftEndMinute >= requestEndMinute;
    }

    private boolean sameShiftWindows(
            List<UploadedShift> left,
            List<UploadedShift> right) {

        return normalizedWindows(left).equals(normalizedWindows(right));
    }

    private List<String> normalizedWindows(List<UploadedShift> shifts) {
        if (shifts == null || shifts.isEmpty()) {
            return List.of();
        }

        return shifts.stream()
                .map(shift -> shift.getStartTime() + "|" + shift.getEndTime())
                .sorted()
                .toList();
    }

    private LocalTime effectiveStart(
            DriverAssignment assignment,
            TransportationDemandAnalysis demand) {

        if (assignment.getScheduledStartTime() != null) {
            return assignment.getScheduledStartTime();
        }

        TransportationDemandPeriod period = demandPeriod(
                demand,
                assignment.getPeriod()
        );
        return period == null ? null : period.getRecommendedStartTime();
    }

    private LocalTime effectiveEnd(
            DriverAssignment assignment,
            TransportationDemandAnalysis demand) {

        if (assignment.getScheduledEndTime() != null) {
            return assignment.getScheduledEndTime();
        }

        TransportationDemandPeriod period = demandPeriod(
                demand,
                assignment.getPeriod()
        );
        return period == null ? null : period.getRecommendedEndTime();
    }

    private TransportationDemandPeriod demandPeriod(
            TransportationDemandAnalysis demand,
            ShuttlePeriod period) {

        if (demand == null || period == null) {
            return null;
        }

        return switch (period) {
            case MORNING -> demand.getMorning();
            case MIDDAY -> demand.getMidday();
            case AFTERNOON -> demand.getEvening();
        };
    }

    private String formatDriverSchedules(
            List<DriverSchedule> schedules,
            Long driverId) {

        if (schedules == null || schedules.isEmpty() || driverId == null) {
            return null;
        }

        String formatted = schedules.stream()
                .filter(schedule -> driverId.equals(schedule.driverId()))
                .sorted(Comparator.comparing(DriverSchedule::startTime))
                .map(schedule -> formatWindow(schedule.startTime(), schedule.endTime()))
                .collect(Collectors.joining(", "));

        return formatted.isBlank() ? null : formatted;
    }

    private String formatShifts(List<UploadedShift> shifts) {
        if (shifts == null || shifts.isEmpty()) {
            return "Not scheduled";
        }

        return shifts.stream()
                .map(shift -> formatWindow(shift.getStartTime(), shift.getEndTime()))
                .collect(Collectors.joining(", "));
    }

    private String formatPlannedWindows(DriverAssignment assignment) {
        List<PlannedDrivingWindow> windows = assignment.getPlannedDrivingWindows();
        if (windows.isEmpty()) {
            return null;
        }

        String formatted = windows.stream()
                .map(window -> formatWindow(window.getStartTime(), window.getEndTime()))
                .collect(Collectors.joining(", "));

        return assignment.getPeriod() == ShuttlePeriod.MIDDAY
                ? "planned midday windows " + formatted
                : formatted;
    }

    private String formatWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return null;
        }
        return start.format(TIME_FORMATTER) + "–" + end.format(TIME_FORMATTER);
    }

    private String periodLabel(ShuttlePeriod period) {
        return switch (period) {
            case MORNING -> "Morning";
            case MIDDAY -> "Midday Turnover";
            case AFTERNOON -> "Afternoon";
        };
    }

    private int toMinute(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private int normalizeEnd(int startMinute, int endMinute) {
        return endMinute <= startMinute ? endMinute + 24 * 60 : endMinute;
    }
}
