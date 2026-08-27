package com.victorpena.shuttleflow.service;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.victorpena.shuttleflow.repository.OrganizationMembershipRepository;

@Service
@Transactional(readOnly = true)
public class CurrentOrganizationService {

    private final OrganizationMembershipRepository
            organizationMembershipRepository;

    public CurrentOrganizationService(
            OrganizationMembershipRepository
                    organizationMembershipRepository) {

        this.organizationMembershipRepository =
                organizationMembershipRepository;
    }

    public Long requireOrganizationId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "An authenticated user is required."
            );
        }

        List<Long> organizationIds =
                organizationMembershipRepository
                        .findOrganizationIdsByUserEmail(
                                authentication.getName()
                        );

        if (organizationIds.isEmpty()) {
            throw new IllegalStateException(
                    "The signed-in account does not belong "
                            + "to an organization."
            );
        }

        if (organizationIds.size() > 1) {
            throw new IllegalStateException(
                    "The signed-in account belongs to more than "
                            + "one organization. Organization selection "
                            + "must be implemented before continuing."
            );
        }

        return organizationIds.get(0);
    }
}