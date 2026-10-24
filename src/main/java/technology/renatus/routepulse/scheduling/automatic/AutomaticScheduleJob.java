package technology.renatus.routepulse.scheduling.automatic;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import java.time.Instant;

@Component
public class AutomaticScheduleJob {
    private final OrganizationRepository organizations;
    private final AutomaticScheduleService schedules;
    private final boolean enabled;
    public AutomaticScheduleJob(OrganizationRepository organizations,AutomaticScheduleService schedules,
            @Value("${app.automatic-schedule.enabled:true}") boolean enabled) {
        this.organizations=organizations; this.schedules=schedules; this.enabled=enabled;
    }
    @Scheduled(fixedDelay=300000,initialDelay=30000)
    public void refresh() {
        if(!enabled)return;
        var now=Instant.now();
        for(var org:organizations.findAll()) if(org.hasApplicationAccess(now)) {
            try {schedules.refresh(org.getId());}
            catch(RuntimeException error) {LoggerFactory.getLogger(getClass()).error("Automatic driving schedule refresh failed for organization {}",org.getId(),error);}
        }
    }
}
