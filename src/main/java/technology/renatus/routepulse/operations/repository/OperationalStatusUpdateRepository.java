package technology.renatus.routepulse.operations.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.operations.model.OperationalStatusUpdate;

public interface OperationalStatusUpdateRepository
        extends JpaRepository<OperationalStatusUpdate, Long> {

    Optional<OperationalStatusUpdate>
            findFirstByOrganizationIdOrderByUpdatedAtDescIdDesc(Long organizationId);
}
