package com.victorpena.shuttleflow.provider;

import java.time.LocalDate;
import java.util.List;

import com.victorpena.shuttleflow.model.ShiftRecord;

public interface ScheduleProvider {

    List<ShiftRecord> getSchedulesForDate(
            Long organizationId,
            LocalDate date
    );
}