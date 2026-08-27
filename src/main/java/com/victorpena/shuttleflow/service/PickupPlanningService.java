package com.victorpena.shuttleflow.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.victorpena.shuttleflow.model.PickupPriority;
import com.victorpena.shuttleflow.model.PickupWindow;
import com.victorpena.shuttleflow.model.ShiftRecord;
import com.victorpena.shuttleflow.provider.ScheduleProvider;

@Service
public class PickupPlanningService {

    private static final int WINDOW_MINUTES = 15;

    private final ScheduleProvider scheduleProvider;

    public PickupPlanningService(
            ScheduleProvider scheduleProvider) {

        this.scheduleProvider = scheduleProvider;
    }

    public List<PickupWindow> getPickupWindowsForDate(
            Long organizationId,
            LocalDate date) {

        List<ShiftRecord> shifts =
                scheduleProvider.getSchedulesForDate(
                        organizationId,
                        date
                );

        Map<LocalTime, Long> shiftCounts =
                shifts.stream()
                        .collect(Collectors.groupingBy(
                                shift ->
                                        calculateWindowStart(
                                                shift.getEndTime()
                                        ),
                                Collectors.counting()
                        ));

        return shiftCounts.entrySet()
                .stream()
                .sorted(
                        Map.Entry.comparingByKey()
                )
                .map(entry ->
                        createPickupWindow(
                                entry.getKey(),
                                entry.getValue()
                        )
                )
                .toList();
    }

    private PickupWindow createPickupWindow(
            LocalTime windowStart,
            long teamMemberCount) {

        int count =
                Math.toIntExact(
                        teamMemberCount
                );

        return new PickupWindow(
                windowStart,
                windowStart.plusMinutes(
                        WINDOW_MINUTES
                ),
                count,
                determinePriority(count)
        );
    }

    private LocalTime calculateWindowStart(
            LocalTime endTime) {

        int roundedMinute =
                (endTime.getMinute()
                        / WINDOW_MINUTES)
                        * WINDOW_MINUTES;

        return endTime
                .withMinute(roundedMinute)
                .withSecond(0)
                .withNano(0);
    }

    private PickupPriority determinePriority(
            int count) {

        if (count >= 6) {
            return PickupPriority.HIGH;
        }

        if (count >= 3) {
            return PickupPriority.MEDIUM;
        }

        return PickupPriority.LOW;
    }
}