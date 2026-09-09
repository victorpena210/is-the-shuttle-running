package technology.renatus.routepulse.organization.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;

public interface OrganizationInvitationRepository
        extends JpaRepository<
                OrganizationInvitation,
                Long> {

    Optional<OrganizationInvitation>
            findByOrganization_IdAndInvitedEmailIgnoreCase(
                    Long organizationId,
                    String invitedEmail
            );

    List<OrganizationInvitation>
            findAllByOrganization_IdAndRoleOrderByCreatedAtDesc(
                    Long organizationId,
                    OrganizationRole role
            );

    @Query("""
            SELECT invitation
            FROM OrganizationInvitation invitation
            JOIN FETCH invitation.organization
            LEFT JOIN FETCH invitation.driver
            WHERE invitation.tokenHash = :tokenHash
            """)
    Optional<OrganizationInvitation>
            findByTokenHash(
                    @Param("tokenHash")
                    String tokenHash
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM OrganizationInvitation invitation
            JOIN FETCH invitation.organization
            LEFT JOIN FETCH invitation.driver
            WHERE invitation.tokenHash = :tokenHash
            """)
    Optional<OrganizationInvitation>
            findByTokenHashForUpdate(
                    @Param("tokenHash")
                    String tokenHash
            );
}
