package technology.renatus.routepulse.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import technology.renatus.routepulse.model.ScheduleImport;

public interface ScheduleImportRepository
        extends JpaRepository<ScheduleImport, Long> {

    Optional<ScheduleImport>
            findFirstByOrganization_IdOrderByUploadedAtDesc(
                    Long organizationId
            );

    @EntityGraph(attributePaths = "uploadedBy")
    List<ScheduleImport>
            findTop10ByOrganization_IdOrderByUploadedAtDesc(
                    Long organizationId
            );
}