package com.victorpena.shuttleflow.provider;


import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.victorpena.shuttleflow.model.ShiftRecord;

public class MockScheduleProvider implements ScheduleProvider {

    @Override
    public List<ShiftRecord> getSchedulesForDate(LocalDate date) {
        return List.of(
            new ShiftRecord(
                "1001",
                "Test Employee 1",
                "Food Service",
                "Team Member",
                date,
                LocalTime.of(9, 0),
                LocalTime.of(15, 2),
                "Employee Gate"
            ),
            new ShiftRecord(
                "1002",
                "Test Employee 2",
                "Retail",
                "Cashier",
                date,
                LocalTime.of(9, 0),
                LocalTime.of(15, 8),
                "Employee Gate"
            ),
            new ShiftRecord(
                "1003",
                "Test Employee 3",
                "Rides",
                "Ride Operator",
                date,
                LocalTime.of(10, 0),
                LocalTime.of(15, 19),
                "Employee Gate"
            )
        );
    }


}