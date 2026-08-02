package com.victorpena.shuttleflow.calendar;

import java.util.List;

public record SixFlagsOperating(
        int operatingTypeId,
        String operatingTypeName,
        List<SixFlagsOperatingItem> items
) {
}