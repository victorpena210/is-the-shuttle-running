package com.victorpena.shuttleflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.victorpena.shuttleflow.model.OrganizationMembership;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembership, Long> {

    List<OrganizationMembership> findAllByUser_Id(Long userId);

    Optional<OrganizationMembership>
            findByOrganization_IdAndUser_Id(
                    Long organizationId,
                    Long userId
            );
}