package technology.renatus.routepulse.driver.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import technology.renatus.routepulse.driver.model.Driver;

public interface DriverRepository
        extends JpaRepository<Driver, Long> {

    boolean existsByOrganization_IdAndEmployeeId(
            Long organizationId,
            String employeeId
    );

    boolean existsByOrganization_IdAndEmailIgnoreCase(
            Long organizationId,
            String email
    );

    boolean existsByOrganization_IdAndEmailIgnoreCaseAndIdNot(
            Long organizationId,
            String email,
            Long driverId
    );

    List<Driver>
            findAllByOrganization_IdOrderByNameAsc(
                    Long organizationId
            );

    List<Driver>
            findAllByOrganization_IdAndActiveTrueOrderByNameAsc(
                    Long organizationId
            );

    Optional<Driver>
            findByIdAndOrganization_Id(
                    Long id,
                    Long organizationId
            );

    Optional<Driver>
            findByOrganization_IdAndUser_Id(
                    Long organizationId,
                    Long userId
            );
}
