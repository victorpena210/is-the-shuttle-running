package com.victorpena.shuttleflow.provider;

import com.victorpena.shuttleflow.model.ShiftRecord;
import java.time.LocalDate;
import java.util.List;

public interface ScheduleProvider {
    List<ShiftRecord> getSchedulesForDate(LocalDate date);
}