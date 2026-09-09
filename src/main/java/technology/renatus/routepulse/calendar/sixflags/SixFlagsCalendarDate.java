package technology.renatus.routepulse.calendar.sixflags;

import java.util.List;

public record SixFlagsCalendarDate(
        String date,
        boolean isParkClosed,
        List<SixFlagsOperating> operatings
) {
}
