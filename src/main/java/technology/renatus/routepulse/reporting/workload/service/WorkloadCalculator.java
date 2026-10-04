package technology.renatus.routepulse.reporting.workload.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.reporting.workload.model.DriverWorkloadRow;
import technology.renatus.routepulse.reporting.workload.model.WorkloadSummary;

/** Calculates scheduled and actual totals separately from already-loaded records. */
@Component
public class WorkloadCalculator {

    public WorkloadSummary calculate(
            LocalDate fromDate, LocalDate toDate, Long driverId,
            List<Driver> organizationDrivers, List<DriverAssignment> assignments) {
        return calculate(fromDate, toDate, driverId, organizationDrivers, assignments, List.of());
    }

    public WorkloadSummary calculate(
            LocalDate fromDate, LocalDate toDate, Long driverId,
            List<Driver> organizationDrivers, List<DriverAssignment> assignments,
            List<DrivingSession> unscheduledSessions) {

        Map<Long, MutableDriverWorkload> workloadByDriver =
                new LinkedHashMap<>();

        /*
         * Create one workload bucket for every driver.
         *
         * When a driver filter is active, only create a bucket
         * for that driver.
         */
        for (Driver driver : organizationDrivers) {

            if (driverId == null
                    || Objects.equals(
                            driver.getId(),
                            driverId
                    )) {

                workloadByDriver.put(
                        driver.getId(),
                        new MutableDriverWorkload(driver)
                );
            }
        }

        for (DriverAssignment assignment : assignments) {

            Driver scheduledDriver =
                    assignment.getScheduledDriver();

            Long scheduledDriverId =
                    scheduledDriver.getId();

            MutableDriverWorkload scheduledWorkload =
                    workloadByDriver.get(
                            scheduledDriverId
                    );

            /*
             * Scheduled time always belongs to the
             * scheduled driver.
             */
            if (scheduledWorkload != null) {

                scheduledWorkload.scheduledMinutes +=
                        Math.max(
                                0,
                                assignment.getScheduledMinutes()
                        );

                scheduledWorkload.scheduledShifts++;
            }

            /*
             * Keep track of which actual drivers participated
             * in this assignment.
             *
             * This prevents multiple sessions/scans from counting
             * as multiple "shifts driven" for the same driver.
             */
            Set<Long> actualDriverIdsForAssignment =
                    new LinkedHashSet<>();

            for (DrivingSession session
                    : assignment.getDrivingSessions()) {

                Driver actualDriver =
                        session.getDriver();

                if (actualDriver == null
                        || actualDriver.getId() == null) {
                    continue;
                }

                Long actualDriverId =
                        actualDriver.getId();

                boolean firstSessionForDriverOnAssignment =
                        actualDriverIdsForAssignment.add(
                                actualDriverId
                        );

                MutableDriverWorkload actualWorkload =
                        workloadByDriver.get(
                                actualDriverId
                        );

                if (actualWorkload == null) {
                    continue;
                }

                /*
                 * Actual time only belongs to the person
                 * who actually drove.
                 *
                 * Only completed sessions have final duration.
                 */
                if (session.isCompleted()) {

                    actualWorkload.actualMinutes +=
                            Math.max(
                                    0,
                                    session.getActualMinutes()
                            );
                }

                /*
                 * Count this driver as having driven the
                 * assignment once, regardless of the number
                 * of sessions they created.
                 */
                if (firstSessionForDriverOnAssignment) {

                    actualWorkload.shiftsDriven++;

                    /*
                     * This driver drove an assignment that
                     * was scheduled to somebody else.
                     */
                    if (!Objects.equals(
                            scheduledDriverId,
                            actualDriverId
                    )) {

                        actualWorkload
                                .coveredForAnotherDriver++;
                    }
                }
            }

            /*
             * Determine whether somebody besides the scheduled
             * driver participated in the assignment.
             */
            if (scheduledWorkload != null) {

                boolean anotherDriverParticipated =
                        actualDriverIdsForAssignment
                                .stream()
                                .anyMatch(
                                        actualDriverId ->
                                                !Objects.equals(
                                                        actualDriverId,
                                                        scheduledDriverId
                                                )
                                );

                if (anotherDriverParticipated) {

                    scheduledWorkload
                            .coveredByAnotherDriver++;
                }
            }
        }

        // Unscheduled segments add actual time; scheduled shifts and coverage counts remain unchanged.
        for (DrivingSession session : unscheduledSessions) {
            MutableDriverWorkload workload = workloadByDriver.get(session.getDriver().getId());
            if (workload != null && session.isCompleted()) {
                workload.actualMinutes += session.getActualMinutes();
            }
        }

        List<DriverWorkloadRow> rows =
                workloadByDriver
                        .values()
                        .stream()
                        .map(
                                MutableDriverWorkload::toRow
                        )
                        .toList();

        long scheduledMinutes =
                rows.stream()
                        .mapToLong(
                                DriverWorkloadRow::getScheduledMinutes
                        )
                        .sum();

        long actualMinutes =
                rows.stream()
                        .mapToLong(
                                DriverWorkloadRow::getActualMinutes
                        )
                        .sum();

        int scheduledShifts =
                rows.stream()
                        .mapToInt(
                                DriverWorkloadRow::getScheduledShifts
                        )
                        .sum();

        int shiftsDriven =
                rows.stream()
                        .mapToInt(
                                DriverWorkloadRow::getShiftsDriven
                        )
                        .sum();

        return new WorkloadSummary(
                fromDate,
                toDate,
                scheduledMinutes,
                actualMinutes,
                scheduledShifts,
                shiftsDriven,
                rows
        );
    }

    private static final class MutableDriverWorkload {

        private final Driver driver;

        private long scheduledMinutes;
        private long actualMinutes;

        private int scheduledShifts;
        private int shiftsDriven;

        private int coveredByAnotherDriver;
        private int coveredForAnotherDriver;

        private MutableDriverWorkload(
                Driver driver) {

            this.driver = driver;
        }

        private DriverWorkloadRow toRow() {

            return new DriverWorkloadRow(
                    driver,
                    scheduledMinutes,
                    actualMinutes,
                    scheduledShifts,
                    shiftsDriven,
                    coveredByAnotherDriver,
                    coveredForAnotherDriver
            );
        }
    }
}
