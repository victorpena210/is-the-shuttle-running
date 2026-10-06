package technology.renatus.routepulse.reporting.history;

import java.time.*;
import java.util.*;
import static technology.renatus.routepulse.reporting.history.ServiceReviewCalculator.*;

/** Executable without Spring or a database; also called by JUnit. */
public final class ServiceReviewChecks {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final ServiceReviewCalculator CALC = new ServiceReviewCalculator(ZoneOffset.UTC, 10, 15);
    private static final Span WINDOW = new Span(at(9, 0), at(9, 15));
    private static final Assignment PLAN = new Assignment(1L, 10L, "Scheduled Driver", "PENDING");
    private static int checks;
    public static void main(String[] args) { verify(); System.out.println("PASS: " + checks + " service-review checks"); }
    public static void verify() {
        checks = 0;
        Activity unscheduled = new Activity(1L, 20L, "Replacement", true, at(9, 0), at(9, 15));
        Ride ride = new Ride(1L, 20L, "Replacement", at(9, 5));
        Input input = input("Open", "Uploaded schedule", List.of(PLAN), List.of(unscheduled), List.of(ride));
        Row row = CALC.calculate(input, null, "actual", at(10, 0));
        equal(900L, row.seconds(), "unscheduled minutes included");
        equal(900L, row.unscheduledSeconds(), "unscheduled subset");
        equal(1L, row.confirmations(), "unscheduled QR included");
        equal(false, row.needsReview(), "unscheduled activity corroborates service");
        equal("both", row.result(), "both evidence sources");
        equal("Replacement", row.actualDrivers(), "actual and scheduled drivers stay separate");
        equal("Scheduled Driver", row.scheduledDrivers(), "planned driver retained");

        Row gap = CALC.calculate(input("Open", "Uploaded schedule", List.of(PLAN), List.of(), List.of()), null, "actual", at(10, 0));
        equal(true, gap.needsReview(), "elapsed demand without evidence needs review");
        equal(1, gap.gaps(), "one gap window");
        equal(true, matches(gap, "needs-review"), "review filter");
        equal(false, matches(row, "none"), "evidence filter excludes corroborated activity");
        for (String state : List.of("Park closed", "Calendar unverified")) {
            Row closed = CALC.calculate(input(state, "Uploaded schedule", List.of(), List.of(unscheduled), List.of(ride)), null, "actual", at(10, 0));
            equal(false, closed.needsReview(), "nonoperating day has no gap flags");
            equal(900L, closed.seconds(), "activity retained on closed/unverified day");
            equal(true, closed.state().startsWith(state), "calendar state shown instead of upcoming");
        }
        Row absent = CALC.calculate(input("Open", "No schedule available", List.of(), List.of(unscheduled), List.of()), null, "actual", at(10, 0));
        equal(false, absent.needsReview(), "missing forecast is not zero demand proof");
        equal("No schedule available", absent.state(), "unavailable schedule labeled");
        Row archived = CALC.calculate(input("Open", "Archived schedule totals", List.of(), List.of(), List.of()), null, "actual", at(10, 0)).withDemandTotal(56);
        equal(false, archived.needsReview(), "archived totals cannot establish a timed service gap");
        equal(56, archived.demand(), "authoritative archived period totals retained");
        equal(true, archived.state().contains("Archived demand"), "archive timing limitation is visible");
        Row future = CALC.calculate(input("Open", "Uploaded schedule", List.of(), List.of(), List.of()), null, "actual", at(8, 0));
        equal("Upcoming", future.state(), "future state");
        equal(false, future.needsReview(), "future periods not evaluated");
        Row inProgress = CALC.calculate(input("Open", "Uploaded schedule", List.of(PLAN), List.of(), List.of()), null, "actual", at(9, 10));
        equal("In progress", inProgress.state(), "active window distinct from upcoming");
        equal(false, inProgress.needsReview(), "unfinished window not evaluated");
        Row unassigned = CALC.calculate(input("Open", "Uploaded schedule", List.of(), List.of(), List.of()), null, "actual", at(10, 0));
        equal("No scheduled assignment", unassigned.state(), "missing assignment clear");
        for (String outcome : List.of("CANCELLED", "EXCUSED", "MISSED")) {
            Row reviewed = CALC.calculate(input("Open", "Uploaded schedule", List.of(new Assignment(1L,10L,"Driver",outcome)), List.of(), List.of()), null, "actual", at(10, 0));
            equal(false, reviewed.needsReview(), "reviewed outcome not flagged again");
        }
        equal(null, CALC.calculate(input, 99L, "actual", at(10, 0)), "unrelated actual driver excluded");
        equal(null, CALC.calculate(input, 20L, "scheduled", at(10, 0)), "substitute not mistaken for scheduled driver");
        equal(900L, CALC.calculate(input, 10L, "scheduled", at(10, 0)).seconds(), "scheduled driver filter retains substitute evidence");
        equal(900L, CALC.calculate(input, 20L, "actual", at(10, 0)).seconds(), "actual driver filter includes their service");
        var overlaps = List.of(unscheduled, new Activity(2L,30L,"Other",false,at(9,5),at(9,20)));
        equal(900L, CALC.calculate(input("Open","Uploaded schedule",List.of(PLAN),overlaps,List.of()),null,"actual",at(10,0)).seconds(), "overlapping sessions unioned within scope");
        equal(900L, summarize(List.of(row,row), at(10,0)).seconds(), "overlapping period totals deduplicated");
        equal(1L, summarize(List.of(row,row), at(10,0)).confirmations(), "QR totals deduplicated");
        Activity ongoing = new Activity(3L,20L,"Replacement",true,at(9,0),null);
        equal(300L, CALC.calculate(input("Open","Uploaded schedule",List.of(),List.of(ongoing),List.of()),null,"actual",at(9,5)).seconds(), "active session capped at now");
        Activity overnight = new Activity(4L,20L,"Night Driver",true,DAY.minusDays(1).atTime(23,45).toInstant(ZoneOffset.UTC),at(0,15));
        equal(900L, serviceSeconds(List.of(overnight),List.of(new Span(at(0,0),at(1,0))),at(10,0)), "overnight session clipped at day boundary");
        Activity partial = new Activity(5L,20L,"Replacement",true,at(9,0),at(9,0).plusSeconds(20));
        Row shortRun = CALC.calculate(input("Open","Uploaded schedule",List.of(),List.of(partial),List.of()),null,"actual",at(10,0));
        equal(false, shortRun.needsReview(), "subminute activity not treated as absent");
        equal("<1 min", shortRun.drivingTime(), "subminute activity displayed accurately");
        Row noQr = CALC.calculate(input("Open","Uploaded schedule",List.of(),List.of(unscheduled),List.of()),null,"actual",at(10,0));
        equal("driving-only", noQr.result(), "optional scans missing shown as driving only");
        equal(false, noQr.needsReview(), "missing optional scans alone does not flag missed service");
        var none = new Input(DAY,"morning","Morning","Open","No schedule available",List.of(WINDOW),List.of(),List.of(),List.of(unscheduled),List.of());
        equal(1, CALC.calculate(none,null,"actual",at(10,0)).windows().size(), "activity visible without forecast demand");
        var optional = new Input(DAY,"midday","Midday","Open","Uploaded schedule",List.of(WINDOW),
                List.of(new Demand(WINDOW,20)),List.of(),List.of(),List.of());
        var optionalRow = CALC.calculate(optional,null,"actual",at(10,0));
        equal(false, optionalRow.needsReview(), "unassigned optional midday does not require review");
        equal("Optional midday—not assigned", optionalRow.state(), "optional midday status");
        var accepted = new Input(DAY,"midday","Midday","Open","Uploaded schedule",List.of(WINDOW),
                List.of(new Demand(WINDOW,20)),List.of(PLAN),List.of(),List.of());
        equal(true, CALC.calculate(accepted,null,"actual",at(10,0)).needsReview(), "accepted midday is evaluated");
        var office = new Input(DAY,"morning","Morning","Open","Uploaded schedule",List.of(WINDOW),
                List.of(new Demand(WINDOW,20)),List.of(),List.of(unscheduled),List.of(ride),"Wardrobe office coverage required");
        var officeRow = CALC.calculate(office,null,"actual",at(10,0));
        equal(false, officeRow.needsReview(), "office exemption does not require service review");
        equal(900L, officeRow.seconds(), "office exemption preserves actual driving evidence");
        var eveningWindows = List.of(new Span(at(18, 45), at(19, 0)), new Span(at(19, 0), at(19, 15)));
        var eveningInput = new Input(DAY,"evening","Evening","Open","Uploaded schedule",eveningWindows,
                eveningWindows.stream().map(w -> new Demand(w,20)).toList(),List.of(),List.of(),List.of(),null,at(19,0));
        var eveningRow = CALC.calculate(eveningInput,null,"actual",at(20,0));
        equal(1, eveningRow.gaps(), "only demand after wardrobe closes requires service evidence");
        equal("Before wardrobe closes—driving not required", eveningRow.windows().get(0).status(), "pre-close demand is labeled");
        equal(40, eveningRow.demand(), "pre-close demand stays visible");
        var partialClosing = new Input(DAY,"evening","Evening","Open","Uploaded schedule",List.of(WINDOW),
                List.of(new Demand(WINDOW,20)),List.of(),List.of(unscheduled),List.of(),null,at(9,10));
        equal(false, CALC.calculate(partialClosing,null,"actual",at(10,0)).needsReview(), "post-close evidence in partial window counts");
        var beforeOnly = new Activity(8L,20L,"Driver",true,at(9,0),at(9,5));
        partialClosing = new Input(DAY,"evening","Evening","Open","Uploaded schedule",List.of(WINDOW),
                List.of(new Demand(WINDOW,20)),List.of(),List.of(beforeOnly),List.of(),null,at(9,10));
        equal(true, CALC.calculate(partialClosing,null,"actual",at(10,0)).needsReview(), "pre-close activity cannot cover the later required window");
        ServiceReviewFilters today = filters(null,null,null);
        equal(DAY,today.from(),"default is today");
        equal(DAY,today.to(),"default single day");
        equal(DAY.minusDays(1),filters(null,null,"yesterday").from(),"yesterday preset");
        equal(DAY,filters(null,null,"week").from(),"week starts Monday");
        equal(DAY.minusDays(1),filters(null,DAY.minusDays(1),null).from(),"one range bound fills other");
        rejects(() -> filters(DAY,DAY.minusDays(1),null),"reversed dates rejected");
        rejects(() -> filters(DAY,DAY.plusDays(31),null),"oversized range rejected");
        equal(DAY.plusDays(30),filters(DAY,DAY.plusDays(30),null).to(),"31 days accepted");
        rejects(() -> ServiceReviewFilters.resolve(null,null,null,"invalid",null,null,null,DAY),"invalid period rejected");
        // Spring DST transitions use instants rather than assuming every date contains 24 hours.
        ZoneId chicago = ZoneId.of("America/Chicago");
        LocalDate dst = LocalDate.of(2026,11,1);
        Span longDay = new Span(dst.atStartOfDay(chicago).toInstant(),dst.plusDays(1).atStartOfDay(chicago).toInstant());
        equal(90000L, serviceSeconds(List.of(new Activity(9L,20L,"Night",true,longDay.start(),longDay.end())),List.of(longDay),longDay.end()),"25-hour DST day retained");
    }
    private static Input input(String calendar,String source,List<Assignment> assignments,List<Activity> activity,List<Ride> rides) {
        return new Input(DAY,"morning","Morning",calendar,source,List.of(WINDOW),List.of(new Demand(WINDOW,20)),assignments,activity,rides);
    }
    private static ServiceReviewFilters filters(LocalDate from,LocalDate to,String range) {
        return ServiceReviewFilters.resolve(from,to,range,null,null,null,null,DAY);
    }
    private static Instant at(int hour,int minute) { return DAY.atTime(hour,minute).toInstant(ZoneOffset.UTC); }
    private static void equal(Object expected,Object actual,String label) {
        checks++;
        if (!Objects.equals(expected,actual)) throw new AssertionError(label+": expected "+expected+", got "+actual);
    }
    private static void rejects(Runnable action,String label) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(label);
    }
}
