package technology.renatus.routepulse.calendar;

import java.util.List;

public record SixFlagsCalendarDate(
        String date,
        boolean isParkClosed,
        List<SixFlagsOperating> operatings
) {
}