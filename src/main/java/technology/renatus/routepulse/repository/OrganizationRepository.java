package technology.renatus.routepulse.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import technology.renatus.routepulse.model.Organization;

public interface OrganizationRepository
        extends JpaRepository<Organization, Long> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT organization
            FROM Organization organization
            WHERE organization.id = :organizationId
            """)
    Optional<Organization> findByIdForUpdate(
            @Param("organizationId")
            Long organizationId
    );
}