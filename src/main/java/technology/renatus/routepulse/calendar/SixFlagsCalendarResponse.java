package technology.renatus.routepulse.calendar;

import java.util.List;


public record SixFlagsCalendarResponse(
		int parkId,
		String parkAbbreviation,
		String parkName,
		List<SixFlagsCalendarDate> dates
		) {

}
