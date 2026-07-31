package com.victorpena.shuttleflow.calendar;

import java.util.List;

public record SixFlagsCalendarDate(
        String date,
        boolean isParkClosed,
        List<SixFlagsOperating> operatings
) {
}