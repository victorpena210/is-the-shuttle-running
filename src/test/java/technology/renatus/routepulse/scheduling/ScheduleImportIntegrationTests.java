package technology.renatus.routepulse.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.support.MySqlIntegrationTestSupport;

class ScheduleImportIntegrationTests extends MySqlIntegrationTestSupport {

    @Test
    void csvUploadKeepsHistoryButSelectsOnlyTheNewestImportForADate() {
        Organization organization = createOrganization(
                "CSV Organization",
                "csv-organization"
        );

        UserAccount uploader = createUser(
                "uploader@example.com",
                true
        );

        LocalDate shiftDate =
                LocalDate.of(2026, 9, 15);

        ScheduleImport firstImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "first.csv",
                                "100,Old Driver,9/15/2026 8:00 AM,9/15/2026 4:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver"
                        )
                );

        entityManager.flush();

        LocalDateTime oldImportTime =
                LocalDateTime.of(
                        2026,
                        1,
                        1,
                        0,
                        0
                );

        jdbcTemplate.update(
                """
                UPDATE schedule_imports
                SET uploaded_at = ?
                WHERE id = ?
                """,
                Timestamp.valueOf(oldImportTime),
                firstImport.getId()
        );

        jdbcTemplate.update(
                """
                UPDATE uploaded_shifts
                SET imported_at = ?
                WHERE schedule_import_id = ?
                """,
                Timestamp.valueOf(oldImportTime),
                firstImport.getId()
        );

        entityManager.clear();

        ScheduleImport newestImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "newest.csv",
                                "200,Current Driver,9/15/2026 9:00 AM,9/15/2026 5:00 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver"
                        )
                );

        List<ShiftRecord> currentSchedule =
                csvScheduleProvider.getSchedulesForDate(
                        organization.getId(),
                        shiftDate
                );

        assertEquals(
                2,
                scheduleImportRepository.count()
        );

        assertEquals(
                2,
                uploadedShiftRepository.count()
        );

        assertEquals(1, currentSchedule.size());

        assertEquals(
                "200",
                currentSchedule.get(0).getEmployeeId()
        );

        assertEquals(
                "Current Driver",
                currentSchedule.get(0).getEmployeeName()
        );

        assertEquals(
                "South Gate",
                currentSchedule.get(0).getPickupLocation()
        );

        assertEquals(
                1,
                newestImport.getRowCount()
        );

        assertEquals(
                "newest.csv",
                newestImport.getOriginalFilename()
        );

        assertEquals(
                shiftDate,
                csvScheduleProvider
                        .getFirstDateOfLatestImport(
                                organization.getId()
                        )
        );

        assertEquals(
                List.of(
                        "newest.csv",
                        "first.csv"
                ),
                csvScheduleProvider
                        .getRecentImports(
                                organization.getId()
                        )
                        .stream()
                        .map(ScheduleImport::getOriginalFilename)
                        .toList()
        );
    }

    @Test
    void csvUploadRejectsARowWithOnlyOneShiftTime() {
        Organization organization = createOrganization(
                "Malformed CSV Organization",
                "malformed-csv-organization"
        );

        UserAccount uploader = createUser(
                "malformed-csv@example.com",
                true
        );

        assertThrows(
                IllegalStateException.class,
                () -> csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "malformed.csv",
                                "100,Driver,9/15/2026 8:00 AM,,Park/Ops/Transportation/Shuttle/101 North Gate,Driver"
                        )
                )
        );

        assertEquals(
                0,
                scheduleImportRepository.count()
        );

        assertEquals(
                0,
                uploadedShiftRepository.count()
        );
    }
}
