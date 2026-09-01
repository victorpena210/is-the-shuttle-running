package technology.renatus.routepulse.calendar;

import java.util.List;

public record SixFlagsOperating(
        int operatingTypeId,
        String operatingTypeName,
        List<SixFlagsOperatingItem> items
) {
}