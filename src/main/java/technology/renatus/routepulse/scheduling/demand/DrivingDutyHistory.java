package technology.renatus.routepulse.scheduling.demand;

import java.time.*;
import java.util.*;
import technology.renatus.routepulse.driving.model.DrivingSession;

/** Duty rotation follows actual driving that overlapped a captured service window. */
final class DrivingDutyHistory {
    private DrivingDutyHistory() {}
    static Map<ShuttleService,LocalDate> lastServices(List<DrivingSession> sessions,LocalDate before,Instant now) {
        var result=new EnumMap<ShuttleService,LocalDate>(ShuttleService.class);
        for(var session:sessions) {
            var end=session.isActive()?session.getAutomaticEndAt():session.getEndedAt();
            if(end.isAfter(now)) end=now;
            for(var window:session.getCoveragePlan()) {
                if(!window.getServiceDate().isBefore(before)) continue;
                var from=session.getStartedAt().isAfter(window.getStart())?session.getStartedAt():window.getStart();
                var until=end.isBefore(window.getEnd())?end:window.getEnd();
                if(!until.isAfter(from)) continue;
                ShuttleService service;
                try {service=ShuttleService.valueOf(window.getServiceKey());}
                catch(IllegalArgumentException ignored) {continue;}
                result.merge(service,window.getServiceDate(),(a,b)->a.isAfter(b)?a:b);
            }
        }
        return Map.copyOf(result);
    }
}
