package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import technology.renatus.routepulse.model.MorningArrivalWindow;
import technology.renatus.routepulse.model.MorningDrivingPlan;
import technology.renatus.routepulse.model.ShiftRecord;
import technology.renatus.routepulse.provider.ScheduleProvider;

/**
 * Calculates the morning shuttle driving window from scheduled employee
 * clock-in times.
 *
 * This service answers WHEN the shuttle should run. It intentionally does not
 * choose a driver; DriverWorkloadService / DriverAssignmentService continue to
 * own fairness and driver selection.
 */
@Service
public class MorningDrivingPlanningService {

    private final ScheduleProvider scheduleProvider;
    private final LocalTime analysisStart;
    private final LocalTime analysisEnd;
    private final int windowMinutes;
    private final int bufferMinutes;

    public MorningDrivingPlanningService(
            ScheduleProvider scheduleProvider,
            @Value("${app.morning-shuttle.analysis-start:09:00}")
            String analysisStart,
            @Value("${app.morning-shuttle.analysis-end:12:00}")
            String analysisEnd,
            @Value("${app.morning-shuttle.window-minutes:15}")
            int windowMinutes,
            @Value("${app.morning-shuttle.buffer-minutes:15}")
            int bufferMinutes) {

        this.scheduleProvider = scheduleProvider;
        this.analysisStart = LocalTime.parse(analysisStart);
        this.analysisEnd = LocalTime.parse(analysisEnd);

        if (windowMinutes <= 0 || 60 % windowMinutes != 0) {
            throw new IllegalArgumentException(
                    "Morning shuttle window minutes must be a positive divisor of 60."
            );
        }

        if (bufferMinutes < 0) {
            throw new IllegalArgumentException(
                    "Morning shuttle buffer minutes cannot be negative."
            );
        }

        if (this.analysisEnd.isBefore(this.analysisStart)) {
            throw new IllegalArgumentException(
                    "Morning shuttle analysis end must not be before the start."
            );
        }

        this.windowMinutes = windowMinutes;
        this.bufferMinutes = bufferMinutes;
    }

    public MorningDrivingPlan getPlanForDate(
            Long organizationId,
            LocalDate date) {

        List<LocalTime> clockIns = scheduleProvider
                .getSchedulesForDate(
                        organizationId,
                        date
                )
                .stream()
                .map(ShiftRecord::getStartTime)
                .filter(this::isInsideMorningAnalysisRange)
                .sorted()
                .toList();

        if (clockIns.isEmpty()) {
            return MorningDrivingPlan.empty();
        }

        Map<LocalTime, Integer> countsByWindow =
                new TreeMap<>();

        for (LocalTime clockIn : clockIns) {

            LocalTime windowStart =
                    floorToWindow(clockIn);

            countsByWindow.merge(
                    windowStart,
                    1,
                    Integer::sum
            );
        }

        List<MorningArrivalWindow> arrivalWindows =
                countsByWindow
                        .entrySet()
                        .stream()
                        .map(entry ->
                                new MorningArrivalWindow(
                                        entry.getKey(),
                                        entry.getKey().plusMinutes(
                                                windowMinutes
                                        ),
                                        entry.getValue()
                                )
                        )
                        .toList();

        LocalTime firstClockIn =
                clockIns.get(0);

        LocalTime lastClockIn =
                clockIns.get(
                        clockIns.size() - 1
                );

        /*
         * Start early enough to be ready before the first employee arrival.
         *
         * Example:
         *
         * first employee = 9:00 AM
         * buffer = 15 minutes
         *
         * generated driver start = 8:45 AM
         */
        LocalTime scheduledStartTime =
                floorToWindow(
                        firstClockIn.minusMinutes(
                                bufferMinutes
                        )
                );

        /*
         * Keep the shuttle running after the last relevant employee arrival,
         * then round upward to a clean 15-minute boundary.
         *
         * Examples:
         *
         * last clock-in 11:45 -> end 12:00
         * last clock-in 11:59 -> end 12:15
         * last clock-in 12:00 -> end 12:15
         */
        LocalTime scheduledEndTime =
                ceilToWindow(
                        lastClockIn.plusMinutes(
                                bufferMinutes
                        )
                );

        return new MorningDrivingPlan(
                arrivalWindows,
                firstClockIn,
                lastClockIn,
                scheduledStartTime,
                scheduledEndTime,
                clockIns.size()
        );
    }

    private boolean isInsideMorningAnalysisRange(
            LocalTime clockIn) {

        if (clockIn == null) {
            return false;
        }

        return !clockIn.isBefore(
                analysisStart
        )
                && !clockIn.isAfter(
                        analysisEnd
                );
    }

    private LocalTime floorToWindow(
            LocalTime time) {

        int roundedMinute =
                (time.getMinute()
                        / windowMinutes)
                        * windowMinutes;

        return time
                .withMinute(
                        roundedMinute
                )
                .withSecond(0)
                .withNano(0);
    }

    private LocalTime ceilToWindow(
            LocalTime time) {

        LocalTime floored =
                floorToWindow(time);

        if (floored.equals(
                time
                        .withSecond(0)
                        .withNano(0)
        )
                && time.getSecond() == 0
                && time.getNano() == 0) {

            return floored;
        }

        return floored.plusMinutes(
                windowMinutes
        );
    }
}