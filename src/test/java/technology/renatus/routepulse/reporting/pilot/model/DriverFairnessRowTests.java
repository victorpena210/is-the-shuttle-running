package technology.renatus.routepulse.reporting.pilot.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DriverFairnessRowTests {

    @Test
    void formatsNegativeDifferenceWithoutTemplateMath() {
        DriverFairnessRow row = new DriverFairnessRow(
                1L,
                "Demo Driver 4",
                525,
                0,
                1,
                0
        );

        assertEquals("8.8h", row.scheduledHoursLabel());
        assertEquals("0.0h", row.actualHoursLabel());
        assertEquals("-8.8h", row.differenceHoursLabel());
        assertEquals("analytics-table__negative", row.differenceCssClass());
    }

    @Test
    void formatsPositiveAndBalancedDifferences() {
        DriverFairnessRow positive = new DriverFairnessRow(
                2L,
                "Alex",
                60,
                90,
                1,
                1
        );
        DriverFairnessRow balanced = new DriverFairnessRow(
                3L,
                "Jordan",
                60,
                60,
                1,
                1
        );

        assertEquals("+0.5h", positive.differenceHoursLabel());
        assertEquals("analytics-table__positive", positive.differenceCssClass());
        assertEquals("0.0h", balanced.differenceHoursLabel());
        assertEquals("", balanced.differenceCssClass());
    }
}
