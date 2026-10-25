package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.driving.model.*;

/** Called under the same organization lock used by the planner and session starts. */
@Service
public class DrivingCoverageCapture {
    private final AutomaticScheduleRepository schedules;
    private final ZoneId zone;
    public DrivingCoverageCapture(AutomaticScheduleRepository schedules,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.schedules=schedules;this.zone=ZoneId.of(zone);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public void capture(DrivingSession session) {
        var start=session.getStartedAt();var end=session.getAutomaticEndAt();
        var windows=new ArrayList<DrivingCoverageWindow>();
        // Include yesterday's service date for a run after midnight.
        var from=start.atZone(zone).toLocalDate().minusDays(1);
        var until=end.atZone(zone).toLocalDate();
        for(var day:schedules.findAllByOrganizationIdAndDateBetweenOrderByDate(session.getOrganization().getId(),from,until)) {
            var view=day.view(zone,null);
            for(var slot:view.slots()) {
                var a=slot.getStart().atZone(zone).toInstant();var b=slot.getEnd().atZone(zone).toInstant();
                if(slot.getDriverId()!=null && a.isBefore(end) && b.isAfter(start))
                    windows.add(new DrivingCoverageWindow(view.date(),slot.getService().name(),slot.getDriverId(),a,b));
            }
        }
        session.captureCoveragePlan(windows);
    }
}
