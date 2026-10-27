package technology.renatus.routepulse.reporting.rankings;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.service.OrganizationActorService;
import technology.renatus.routepulse.scheduling.demand.DriverScheduleIdentityService;

@Service
@Transactional(readOnly=true)
public class CoverageDetailService {
    private final DrivingSessionRepository sessions;
    private final DriverRepository drivers;
    private final DriverRankingService rankings;
    private final CoverageReviewRepository reviews;
    private final DriverScheduleIdentityService access;
    private final OrganizationActorService actors;
    private final ZoneId zone;
    public CoverageDetailService(DrivingSessionRepository sessions,DriverRepository drivers,DriverRankingService rankings,
            CoverageReviewRepository reviews,DriverScheduleIdentityService access,OrganizationActorService actors,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.sessions=sessions;this.drivers=drivers;this.rankings=rankings;this.reviews=reviews;
        this.access=access;this.actors=actors;this.zone=ZoneId.of(zone);
    }
    public DetailReport build(Long org,Long driverId,DriverRankingService.Period period,boolean manager) {
        return build(org,driverId,period,manager,Instant.now());
    }
    DetailReport build(Long org,Long driverId,DriverRankingService.Period period,boolean manager,Instant now) {
        var driver=drivers.findByIdAndOrganization_Id(driverId,org).orElseThrow(
                ()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Driver was not found."));
        var from=rankings.periodStart(org,period,now);
        var matches=DrivingCoverageCalculator.matches(sessions.findForCoverageDetails(org,driverId,
                from.atStartOfDay(zone).toInstant(),now),from.atStartOfDay(zone).toInstant(),now);
        var total=DrivingCoverageCalculator.summarize(matches).getOrDefault(driverId,new DrivingCoverageCalculator.Total(0,0));
        var names=new HashMap<Long,String>();
        drivers.findAllByOrganization_IdOrderByNameAsc(org).forEach(d->names.put(d.getId(),d.getName()));
        var history=reviews.history(org,driverId);
        var latest=new HashMap<Long,CoverageReview>();
        history.forEach(r->latest.putIfAbsent(r.getSession().getId(),r));
        var groups=new LinkedHashMap<Window,List<DrivingCoverageCalculator.Match>>();
        matches.stream().sorted(Comparator.comparing((DrivingCoverageCalculator.Match m)->m.window().getStart()).reversed()
                .thenComparing(m->m.span().start())).forEach(m->{
            var w=m.window();
            groups.computeIfAbsent(new Window(w.getServiceDate(),w.getServiceKey(),w.getEffectiveScheduledDriverId(),
                    w.getStart(),w.getEnd()),k->new ArrayList<>()).add(m);
        });
        var windows=new ArrayList<WindowRow>();
        for(var entry:groups.entrySet()) {
            var w=entry.getKey();var group=entry.getValue();
            long seconds=DrivingCoverageCalculator.seconds(group.stream().map(DrivingCoverageCalculator.Match::span).toList());
            var parts=group.stream().map(m->{
                var s=m.session();var review=latest.get(s.getId());
                return new Contribution(s.getId(),s.getStartedAt().atZone(zone),s.getEndedAt().atZone(zone),
                        m.span().start().atZone(zone),m.span().end().atZone(zone),
                        Duration.between(m.span().start(),m.span().end()).getSeconds(),
                        review!=null && "FLAGGED".equals(review.getAction()));
            }).distinct().toList();
            windows.add(new WindowRow(w.date(),serviceLabel(w.service()),names.getOrDefault(w.driver(),"Former driver (name unavailable)"),
                    w.start().atZone(zone),w.end().atZone(zone),seconds,
                    seconds>=Duration.between(w.start(),w.end()).getSeconds(),parts));
        }
        var auditedSessions=new LinkedHashMap<Long,ReviewRow>();
        // Keep flagged records reachable even if a time correction removes their coverage.
        for(var review:history) {
            var s=review.getSession();
            if(!auditedSessions.containsKey(s.getId())) auditedSessions.put(s.getId(),new ReviewRow(s.getId(),
                    s.getStartedAt().atZone(zone),review.getId(),"FLAGGED".equals(review.getAction()),manager ? history.stream()
                    .filter(r->r.getSession().getId().equals(s.getId())).map(r->new Audit(r.getAction(),r.getNote(),
                            r.getReviewer().getFirstName()+" "+r.getReviewer().getLastName(),r.getReviewedAt().atZone(zone))).toList():List.of()));
        }
        if(manager) matches.forEach(m->auditedSessions.putIfAbsent(m.session().getId(),new ReviewRow(m.session().getId(),
                m.session().getStartedAt().atZone(zone),0L,false,List.of())));
        return new DetailReport(driverId,driver.getName(),period,from,now.atZone(zone),total.minutes(),total.shifts(),
                List.copyOf(windows),manager?List.copyOf(auditedSessions.values()):List.of());
    }
    @Transactional
    public void review(Long org,Long driverId,String email,Long sessionId,long expectedReviewId,String action,String note) {
        access.requireManager(org,email);
        var s=sessions.findForReview(sessionId,org).filter(session->session.getDriver().getId().equals(driverId))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Session was not found."));
        var previous=reviews.findFirstBySession_IdAndSession_Organization_IdOrderByIdDesc(sessionId,org);
        if(previous.map(CoverageReview::getId).orElse(0L)!=expectedReviewId)
            throw new IllegalStateException("This coverage review changed. Reload the page before saving.");
        if(!"FLAGGED".equals(action) && !"RESOLVED".equals(action))
            throw new IllegalArgumentException("Choose flag for review or resolve review.");
        boolean flagged=previous.map(r->"FLAGGED".equals(r.getAction())).orElse(false);
        if(("FLAGGED".equals(action) && flagged) || ("RESOLVED".equals(action) && !flagged))
            throw new IllegalStateException("This review status has already changed. Reload the page.");
        if("FLAGGED".equals(action) && DrivingCoverageCalculator.matches(List.of(s),Instant.EPOCH,Instant.now()).isEmpty())
            throw new IllegalArgumentException("This session has no completed coverage to flag.");
        String cleaned=note==null?"":note.trim();
        if(cleaned.isEmpty() || cleaned.length()>1000) throw new IllegalArgumentException("Add a reason of 1–1,000 characters.");
        reviews.save(new CoverageReview(s,actors.loadActor(org,email),action,cleaned,Instant.now()));
    }
    static String serviceLabel(String key) {
        return switch(key) {
            case "MORNING_OPENING" -> "Morning opening";
            case "MIDDAY_OPENING" -> "Midday opening";
            case "SCARE_ACTORS" -> "Scare-actor transport";
            case "EMPLOYEE_PICKUP" -> "Employee pickups";
            default -> key.replace('_',' ').toLowerCase(Locale.ROOT);
        };
    }
    static String exactDuration(long seconds) {
        return seconds<60?seconds+" sec":DriverRankingService.duration(seconds/60)+(seconds%60==0?"":" "+seconds%60+" sec");
    }
    private record Window(LocalDate date,String service,Long driver,Instant start,Instant end) {}
    public record Contribution(Long sessionId,ZonedDateTime startedAt,ZonedDateTime endedAt,ZonedDateTime coverageStart,
            ZonedDateTime coverageEnd,long seconds,boolean flagged) {
        public String durationLabel() {return exactDuration(seconds);}
    }
    public record WindowRow(LocalDate date,String service,String scheduledDriver,ZonedDateTime start,ZonedDateTime end,
            long seconds,boolean full,List<Contribution> contributions) {
        public String durationLabel() {return exactDuration(seconds);}
    }
    public record Audit(String action,String note,String reviewer,ZonedDateTime at) {}
    public record ReviewRow(Long sessionId,ZonedDateTime startedAt,long revision,boolean flagged,List<Audit> audit) {}
    public record DetailReport(Long driverId,String driverName,DriverRankingService.Period period,LocalDate from,
            ZonedDateTime updatedAt,long minutes,long shifts,List<WindowRow> windows,List<ReviewRow> reviews) {
        public String durationLabel() {return DriverRankingService.duration(minutes);}
    }
}
