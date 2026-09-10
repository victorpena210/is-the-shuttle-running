package technology.renatus.routepulse.scheduling.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.model.UploadedShift;

public interface UploadedShiftRepository
        extends JpaRepository<UploadedShift, Long> {
	
	Optional<UploadedShift>
    findFirstByOrganization_IdAndEmployeeIdOrderByImportedAtDescIdDesc(
            Long organizationId,
            String employeeId
    );

    /*
     * Return only the newest stored version for this organization/date.
     *
     * While a date is still operational, older uploaded versions remain
     * available for schedule reconciliation. After the operating day closes,
     * retention archives anonymous demand statistics and purges all raw rows.
     * Normal planning reads only the newest import for the selected date.
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

    List<UploadedShift>
            findAllByOrganization_IdAndScheduleImport_IdAndShiftDateAndEmployeeIdOrderByStartTimeAsc(
                    Long organizationId,
                    Long scheduleImportId,
                    LocalDate shiftDate,
                    String employeeId
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
            select distinct uploadedShift.organization.id
            from UploadedShift uploadedShift
            where uploadedShift.shiftDate < :cutoffDate
            order by uploadedShift.organization.id
            """)
    List<Long> findOrganizationIdsWithShiftDateBefore(
            @Param("cutoffDate") LocalDate cutoffDate
    );

    @Query("""
            select distinct uploadedShift.shiftDate
            from UploadedShift uploadedShift
            where uploadedShift.organization.id = :organizationId
              and uploadedShift.shiftDate < :cutoffDate
            order by uploadedShift.shiftDate
            """)
    List<LocalDate> findShiftDatesBefore(
            @Param("organizationId") Long organizationId,
            @Param("cutoffDate") LocalDate cutoffDate
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from UploadedShift uploadedShift
            where uploadedShift.shiftDate < :cutoffDate
            """)
    int deleteShiftRowsBefore(
            @Param("cutoffDate") LocalDate cutoffDate
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
