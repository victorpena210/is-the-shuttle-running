package technology.renatus.routepulse.calendar.sixflags;

import java.util.List;

public record SixFlagsCalendarResponse(
		int parkId,
		String parkAbbreviation,
		String parkName,
		List<SixFlagsCalendarDate> dates
		) {

}
