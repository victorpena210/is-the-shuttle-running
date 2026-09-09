package technology.renatus.routepulse.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.scheduling.domain.DriverSchedule;

class DriverScheduleAvailabilityTests {

    private static final LocalDate DATE =
            LocalDate.of(2026, 9, 7);

    @Test
    void laterClockInClipsGeneratedDrivingStart() {
        DriverSchedule schedule = new DriverSchedule(
                1L,
                "100",
                "Driver One",
                DATE,
                LocalTime.of(9, 0),
                LocalTime.of(16, 0)
        );

        DriverSchedule.DrivingWindow window =
                schedule.clipTo(
                        LocalTime.of(8, 45),
                        LocalTime.of(12, 15)
                );

        assertNotNull(window);
        assertEquals(LocalTime.of(9, 0), window.startTime());
        assertEquals(LocalTime.of(12, 15), window.endTime());
        assertEquals(195, window.minutes());
    }

    @Test
    void earlierClockOutClipsGeneratedDrivingEnd() {
        DriverSchedule schedule = new DriverSchedule(
                1L,
                "100",
                "Driver One",
                DATE,
                LocalTime.of(8, 0),
                LocalTime.of(12, 0)
        );

        DriverSchedule.DrivingWindow window =
                schedule.clipTo(
                        LocalTime.of(8, 45),
                        LocalTime.of(12, 15)
                );

        assertNotNull(window);
        assertEquals(LocalTime.of(8, 45), window.startTime());
        assertEquals(LocalTime.of(12, 0), window.endTime());
    }

    @Test
    void midnightClockOutStillCoversTheMorningWindow() {
        DriverSchedule schedule = new DriverSchedule(
                1L,
                "100",
                "Long Shift Driver",
                DATE,
                LocalTime.of(9, 0),
                LocalTime.MIDNIGHT
        );

        DriverSchedule.DrivingWindow window =
                schedule.clipTo(
                        LocalTime.of(8, 45),
                        LocalTime.of(12, 15)
                );

        assertNotNull(window);
        assertEquals(LocalTime.of(9, 0), window.startTime());
        assertEquals(LocalTime.of(12, 15), window.endTime());
        assertEquals(195, window.minutes());
    }

    @Test
    void shiftWithNoDemandOverlapIsNotEligibleForThatWindow() {
        DriverSchedule schedule = new DriverSchedule(
                1L,
                "100",
                "Evening Driver",
                DATE,
                LocalTime.of(17, 0),
                LocalTime.of(21, 0)
        );

        assertFalse(
                schedule.overlaps(
                        LocalTime.of(8, 45),
                        LocalTime.of(12, 15)
                )
        );

        assertEquals(
                0,
                schedule.overlapMinutes(
                        LocalTime.of(8, 45),
                        LocalTime.of(12, 15)
                )
        );
    }

    @Test
    void ordinaryOverlappingShiftRemainsEligible() {
        DriverSchedule schedule = new DriverSchedule(
                1L,
                "100",
                "Driver One",
                DATE,
                LocalTime.of(9, 0),
                LocalTime.of(16, 0)
        );

        assertTrue(
                schedule.overlaps(
                        LocalTime.of(8, 45),
                        LocalTime.of(12, 15)
                )
        );
    }
}
