package com.victorpena.shuttleflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.victorpena.shuttleflow.model.OrganizationMembership;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, Long> {

    List<OrganizationMembership> findAllByUser_Id(
            Long userId
    );

    Optional<OrganizationMembership>
            findByOrganization_IdAndUser_Id(
                    Long organizationId,
                    Long userId
            );

    @Query("""
            SELECT membership.organization.id
            FROM OrganizationMembership membership
            WHERE LOWER(membership.user.email) = LOWER(:email)
            """)
    List<Long> findOrganizationIdsByUserEmail(
            @Param("email") String email
    );
}