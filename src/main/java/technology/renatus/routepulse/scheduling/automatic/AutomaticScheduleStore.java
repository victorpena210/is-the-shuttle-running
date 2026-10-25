package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.calendar.service.ParkCalendarOverrideService;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.demand.*;
import technology.renatus.routepulse.wardrobe.WardrobeHoursService;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;

@Service
public class AutomaticScheduleStore {
    private final AutomaticScheduleRepository schedules;
    private final OrganizationRepository organizations;
    private final DemandForecastService forecasts;
    private final DriverSuggestionService suggestions;
    private final WardrobeHoursService wardrobe;
    private final ParkCalendarOverrideService overrides;
    private final DriverScheduleIdentityService access;
    private final FrightFestTransportPolicy frightFest;
    private final DrivingSessionRepository sessions;
    private final DailyStaffScheduleService staff;
    private final ZoneId zone;
    public AutomaticScheduleStore(AutomaticScheduleRepository schedules,OrganizationRepository organizations,
            DemandForecastService forecasts,DriverSuggestionService suggestions,WardrobeHoursService wardrobe,
            ParkCalendarOverrideService overrides,DriverScheduleIdentityService access,FrightFestTransportPolicy frightFest,
            DrivingSessionRepository sessions,DailyStaffScheduleService staff,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.schedules=schedules; this.organizations=organizations; this.forecasts=forecasts;
        this.suggestions=suggestions; this.wardrobe=wardrobe; this.overrides=overrides; this.access=access; this.zone=ZoneId.of(zone);
        this.frightFest=frightFest;
        this.sessions=sessions;
        this.staff=staff;
    }
    @Transactional
    public void refresh(Long org,List<ParkOperatingDay> calendar,Instant instant) {
        organizations.findByIdForUpdate(org).orElseThrow(()->new IllegalArgumentException("Organization not found."));
        var now=instant.atZone(zone).toLocalDateTime();
        var planned=new HashMap<Long,Long>();
        for(var observed:calendar.stream().sorted(Comparator.comparing(ParkOperatingDay::date)).toList()) {
            if(observed.date().isBefore(now.toLocalDate())) continue;
            var date=observed.date();
            var saved=schedules.findByOrganizationIdAndDate(org,date).orElseGet(()->new AutomaticScheduleDay(org,date));
            var day=observed;
            var override=overrides.findLatest(org,date);
            if(override.isPresent()) day=new ParkOperatingDay(date,override.get().getOverrideStatus(),
                    observed.openingTime(),observed.closingTime(),observed.buyout(),"MANAGER_CONFIRMATION",null);
            var hours=WardrobeHoursService.withCalendarOpening(wardrobe.getSavedHours(org,date),day);
            String message;
            List<DriverSuggestionService.Window> windows=List.of();
            // Unrecognized opening times require an explicit wardrobe opening; never guess 9 AM.
            if(day.status()==ParkOperatingStatus.CLOSED) message="Park closed. No new driving shifts assigned.";
            else if(day.status()!=ParkOperatingStatus.OPEN) message="Park hours could not be verified. No new shifts assigned; refresh or confirm this date.";
            else if(!hours.usesOpeningWindow()) message="Set wardrobe opening and closing times for this park opening before assigning shifts.";
            else {
                var forecast=forecasts.page(org,date);
                var available=suggestions.forAssignments(org,forecast,instant,hours,frightFest.required(day),day);
                windows=available.periods().stream().flatMap(p->p.windows().stream()).toList();
                message="Opening service and service from wardrobe closing through park closing use saved driver availability. Employee-demand uploads are optional.";
                if(available.noSchedules()) message += " No driver availability is saved for this date.";
                if(frightFest.required(day)) message += " Required scare-actor transport starts at 5 PM; 5:45 PM is an estimate. Continue until finished, or end earlier when complete.";
            }
            var next=AutomaticShiftPlanner.plan(windows,saved.slots(),now,planned);
            saved.update(day.status().name(),day.verificationSource()==null?"NONE":day.verificationSource(),day.openingTime(),day.closingTime(),
                    hours.usesOpeningWindow()?hours.openingStartTime():null,hours.closingTime(),instant,message,next);
            schedules.save(saved);
        }
    }
    @Transactional(readOnly=true)
    public List<AutomaticScheduleDay.View> manager(Long org,LocalDate from,LocalDate to,String email) {
        access.requireManager(org,email); return views(org,from,to,null);
    }
    @Transactional(readOnly=true)
    public List<AutomaticScheduleDay.View> driver(Long org,Long driver,LocalDate from,LocalDate to) {
        return views(org,from,to,driver);
    }
    private List<AutomaticScheduleDay.View> views(Long org,LocalDate from,LocalDate to,Long driver) {
        var now=Instant.now();
        var today=now.atZone(zone).toLocalDate();
        var saved=new TreeMap<LocalDate,AutomaticScheduleDay.View>();
        for(var day:schedules.findAllByOrganizationIdAndDateBetweenOrderByDate(org,from,to)) {
            var view=day.view(zone,driver); saved.put(view.date(),view);
        }
        var start=from.atStartOfDay(zone).toInstant();
        var end=to.plusDays(1).atStartOfDay(zone).toInstant();
        var history=driver==null?sessions.findOverlappingForServiceReview(org,start,end)
                :sessions.findPersonalAnalyticsSessions(org,driver,start,end);
        for(var date=from;!date.isAfter(to)&&!date.isAfter(today);date=date.plusDays(1)) {
            var view=saved.get(date);
            if(view==null) view=new AutomaticScheduleDay.View(date,"HISTORY","DRIVING_HISTORY",null,null,null,null,
                    now.atZone(zone),"No schedule was saved for this date. Recorded driving shows the runs that were logged.",
                    List.of(),List.of(),false);
            saved.put(date,view.withHistory(AutomaticScheduleHistory.forDay(history,date,zone,now)));
        }
        for(var entry:staff.byDay(org,from,to,driver,now).entrySet()) {
            var view=saved.get(entry.getKey());
            if(view==null && !entry.getValue().isEmpty())
                view=new AutomaticScheduleDay.View(entry.getKey(),"HISTORY","NONE",null,null,null,null,
                        now.atZone(zone),"No driving schedule was saved for this date.",List.of(),List.of(),false);
            if(view!=null) saved.put(entry.getKey(),view.withWorkShifts(entry.getValue()));
        }
        return List.copyOf(saved.values());
    }
}
