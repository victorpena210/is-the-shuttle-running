package technology.renatus.routepulse.calendar.sixflags;

import java.util.List;

public record SixFlagsOperating(
        int operatingTypeId,
        String operatingTypeName,
        List<SixFlagsOperatingItem> items
) {
}
