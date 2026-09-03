package technology.renatus.routepulse.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import technology.renatus.routepulse.exception.OrganizationAccessException;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.OrganizationStatus;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.OrganizationRepository;

@Service
@Transactional(readOnly = true)
public class CurrentOrganizationService {

    private final OrganizationMembershipRepository
            organizationMembershipRepository;

    private final OrganizationRepository
            organizationRepository;

    public CurrentOrganizationService(
            OrganizationMembershipRepository
                    organizationMembershipRepository,
            OrganizationRepository organizationRepository) {

        this.organizationMembershipRepository =
                organizationMembershipRepository;

        this.organizationRepository =
                organizationRepository;
    }

    public Long requireOrganizationId(
            Authentication authentication) {

        return requireOrganization(
                authentication
        ).getId();
    }

    public Organization requireOrganization(
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

        Long organizationId =
                organizationIds.get(0);

        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "The organization could not be found."
                                )
                        );

        requireApplicationAccess(
                organization
        );

        return organization;
    }

    private void requireApplicationAccess(
            Organization organization) {

        LocalDateTime now =
                LocalDateTime.now();

        if (organization.hasApplicationAccess(now)) {
            return;
        }

        OrganizationStatus status =
                organization.getStatus();

        String message = switch (status) {

            case TRIAL ->
                    "Your organization's RoutePulse trial has expired.";

            case EXPIRED ->
                    "Your organization's RoutePulse access has expired.";

            case SUSPENDED ->
                    "Your organization's RoutePulse access is suspended.";

            case CANCELLED ->
                    "Your organization's RoutePulse subscription has been cancelled.";

            case ACTIVE ->
                    "Your organization is not currently allowed to use RoutePulse.";
        };

        throw new OrganizationAccessException(
                message
        );
    }
}
