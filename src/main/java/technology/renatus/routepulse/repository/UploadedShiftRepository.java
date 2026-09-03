package technology.renatus.routepulse.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.UploadedShift;

public interface UploadedShiftRepository
        extends JpaRepository<UploadedShift, Long> {

    /*
     * Return only the newest stored version for this organization/date.
     *
     * Older uploaded rows stay in the database permanently for history,
     * but normal RoutePulse planning reads only the rows whose importedAt
     * matches the newest import that contains this date.
     *
     * Using importedAt also keeps this compatible with uploaded_shifts
     * rows that existed before schedule_import_id was introduced in V14.
     */
    @Query("""
            select uploadedShift
            from UploadedShift uploadedShift
            where uploadedShift.organization.id = :organizationId
              and uploadedShift.shiftDate = :shiftDate
              and uploadedShift.importedAt = (
                    select max(latestShift.importedAt)
                    from UploadedShift latestShift
                    where latestShift.organization.id = :organizationId
                      and latestShift.shiftDate = :shiftDate
              )
            order by uploadedShift.startTime asc,
                     uploadedShift.employeeName asc
            """)
    List<UploadedShift> findLatestVersionForDate(
            @Param("organizationId")
            Long organizationId,
            @Param("shiftDate")
            LocalDate shiftDate
    );

    boolean existsByOrganization_Id(
            Long organizationId
    );

    @Query("""
            select distinct uploadedShift.shiftDate
            from UploadedShift uploadedShift
            where uploadedShift.organization.id = :organizationId
            order by uploadedShift.shiftDate
            """)
    List<LocalDate> findAvailableDates(
            @Param("organizationId")
            Long organizationId
    );

    @Query("""
            select min(uploadedShift.shiftDate)
            from UploadedShift uploadedShift
            where uploadedShift.organization.id = :organizationId
              and uploadedShift.importedAt = (
                    select max(latestShift.importedAt)
                    from UploadedShift latestShift
                    where latestShift.organization.id = :organizationId
              )
            """)
    Optional<LocalDate> findFirstDateOfLatestImport(
            @Param("organizationId")
            Long organizationId
    );

    @Query("""
            select distinct uploadedShift.scheduleImport
            from UploadedShift uploadedShift
            where uploadedShift.organization.id = :organizationId
              and uploadedShift.shiftDate = :shiftDate
              and uploadedShift.scheduleImport is not null
              and uploadedShift.importedAt = (
                    select max(latestShift.importedAt)
                    from UploadedShift latestShift
                    where latestShift.organization.id = :organizationId
                      and latestShift.shiftDate = :shiftDate
              )
            """)
    Optional<ScheduleImport> findLatestScheduleImportForDate(
            @Param("organizationId")
            Long organizationId,
            @Param("shiftDate")
            LocalDate shiftDate
    );
}
