package technology.renatus.routepulse.driving.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;

@Service
public class DrivingQrService {

    private final CurrentOrganizationService
            currentOrganizationService;

    private final OrganizationRepository
            organizationRepository;

    public DrivingQrService(
            CurrentOrganizationService
                    currentOrganizationService,
            OrganizationRepository
                    organizationRepository) {

        this.currentOrganizationService =
                currentOrganizationService;

        this.organizationRepository =
                organizationRepository;
    }

    @Transactional
    public Organization regenerateDrivingQrCode(
            Authentication authentication) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        organization.regenerateDrivingQrCode();

        return organizationRepository.save(
                organization
        );
    }
}
