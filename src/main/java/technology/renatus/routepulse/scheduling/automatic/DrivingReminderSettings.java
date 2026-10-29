package technology.renatus.routepulse.scheduling.automatic;

import jakarta.mail.internet.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;

@Service
public class DrivingReminderSettings {
    private final DriverRepository drivers;
    private final OrganizationRepository organizations;
    private final DriverScheduleIdentityService access;
    public DrivingReminderSettings(DriverRepository drivers, OrganizationRepository organizations, DriverScheduleIdentityService access) {
        this.drivers = drivers; this.organizations = organizations; this.access = access;
    }
    @Transactional
    public void update(Long org, Long id, String email, String actor) {
        access.requireManager(org, actor);
        var normalized = normalize(email);
        organizations.findByIdForUpdate(org).orElseThrow();
        var driver = drivers.findByIdAndOrganization_Id(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Driver was not found."));
        driver.changeDrivingReminderEmail(normalized);
    }
    static String normalize(String email) {
        if (email == null || email.isBlank()) return null;
        var value = email.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.length() > 254 || value.contains("\r") || value.contains("\n")) throw new AddressException();
            var address = new InternetAddress(value, true);
            address.validate();
            if (!value.equals(address.getAddress()) || address.isGroup() || address.getPersonal() != null)
                throw new AddressException();
        } catch (AddressException error) {
            throw new IllegalArgumentException("Enter one valid driving reminder email address.");
        }
        return value;
    }
}
