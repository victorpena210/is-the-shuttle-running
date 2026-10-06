package technology.renatus.routepulse.wardrobe;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.domain.*;
import technology.renatus.routepulse.organization.model.*;
import technology.renatus.routepulse.organization.repository.*;
import technology.renatus.routepulse.scheduling.domain.*;

/** Executable checks for date precedence, authorization and the closing cutoff. */
public final class WardrobeHoursChecks {
    private static int checks;
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDate DAY = LocalDate.now(ZONE).plusDays(10);
    public static void main(String[] args) { verify(); System.out.println("PASS: " + checks + " wardrobe-hours checks"); }
    public static void verify() {
        checks = 0;
        Fixture f = new Fixture();
        equal(LocalTime.of(19,0), f.service.getHours(1L, DAY).closingTime(), "default seven");
        f.save("Summer", DAY, DAY.plusDays(29), 17);
        equal(LocalTime.of(17,0), f.service.getHours(1L, DAY).closingTime(), "summer first day inclusive");
        equal(LocalTime.of(17,0), f.service.getHours(1L, DAY.plusDays(29)).closingTime(), "summer last day inclusive");
        equal(LocalTime.of(19,0), f.service.getHours(1L, DAY.plusDays(30)).closingTime(), "outside summer uses default");
        f.save("Fright Fest", DAY.plusDays(30), DAY.plusDays(59), 19);
        equal("Fright Fest", f.service.getHours(1L, DAY.plusDays(30)).label(), "Fright Fest begins on its first date");
        f.service.save(1L, WardrobeHoursRule.Scope.DAY, "Day override", DAY, DAY, LocalTime.of(18,0), "manager@example.com");
        f.save("Updated summer", DAY, DAY.plusDays(29), 16);
        equal(LocalTime.of(18,0), f.service.getHours(1L, DAY).closingTime(), "daily beats newer seasonal rule");
        equal(LocalTime.of(16,0), f.service.getHours(1L, DAY.plusDays(1)).closingTime(), "latest season wins overlap");
        equal(true, f.service.getHours(1L, DAY).dailyOverride(), "daily override labeled");
        f.service.save(1L, WardrobeHoursRule.Scope.DAY, "Use seasonal hours", DAY, DAY, null, "manager@example.com");
        equal(LocalTime.of(16,0), f.service.getHours(1L, DAY).closingTime(), "reset uses latest season");
        equal(false, f.service.getHours(1L, DAY).dailyOverride(), "reset is not an active daily override");
        equal(LocalTime.of(19,0), f.service.getHours(2L, DAY).closingTime(), "other organization is isolated");
        equal(5, f.rows.size(), "audit rows are appended, not overwritten");
        rejects(() -> f.service.save(2L, WardrobeHoursRule.Scope.SEASON, "Foreign", DAY, DAY, LocalTime.NOON, "manager@example.com"), "cross-organization write rejected");
        f.role = OrganizationRole.DRIVER;
        rejects(() -> f.save("Driver change", DAY, DAY, 17), "driver cannot edit hours");
        f.role = OrganizationRole.ADMIN;
        rejects(() -> f.save("Reversed", DAY.plusDays(1), DAY, 17), "reversed date range rejected");
        rejects(() -> f.save("", DAY, DAY, 17), "missing season label rejected");
        rejects(() -> f.save("Past", LocalDate.now(ZONE).minusDays(2), LocalDate.now(ZONE).minusDays(1), 17), "past-only edit rejected");
        rejects(() -> f.service.save(1L, WardrobeHoursRule.Scope.SEASON, "Missing time", DAY, DAY, null, "manager@example.com"), "season requires time");
        equal(5, f.rows.size(), "rejected writes have no effect");
        var laterEdit = new WardrobeHoursRule(1L, WardrobeHoursRule.Scope.SEASON,"Later change",DAY,DAY,LocalTime.of(21,0),1L,
                DAY.plusDays(1).atStartOfDay(ZONE).toInstant());
        equal(LocalTime.of(19,0), WardrobeHoursService.resolve(List.of(laterEdit),DAY,ZONE).closingTime(), "later edit cannot rewrite prior-day hours");
        var raw = period(LocalTime.of(17,0),LocalTime.of(21,0));
        var summer = EveningClosingPolicy.afterClosing(raw,LocalTime.of(17,0));
        var fright = EveningClosingPolicy.afterClosing(raw,LocalTime.of(19,0));
        equal(LocalTime.of(17,0),summer.getRecommendedStartTime(),"summer starts at five");
        equal(LocalTime.of(19,0),fright.getRecommendedStartTime(),"Fright Fest starts at seven");
        equal(LocalTime.of(21,0),fright.getRecommendedEndTime(),"demand end preserved");
        equal(LocalTime.of(17,0),raw.getRecommendedStartTime(),"forecast unchanged");
        equal(false,EveningClosingPolicy.afterClosing(raw,LocalTime.of(21,0)).hasDemand(),"no demand strictly after closing");
        equal(false,EveningClosingPolicy.afterClosing(raw,LocalTime.MIDNIGHT).hasDemand(),"midnight closing is end of day");
        equal(LocalTime.of(19,5),EveningClosingPolicy.afterClosing(raw,LocalTime.of(19,5)).getWindows().get(0).getWindowStart(),"minute cutoff clips actual window");
        equal(LocalTime.of(20,0),EveningClosingPolicy.afterClosing(period(LocalTime.of(20,0),LocalTime.of(21,0)),LocalTime.of(19,0)).getRecommendedStartTime(),"later demand starts later than closing");
        technology.renatus.routepulse.scheduling.importing.ScheduleProvider provider = (org, date) -> List.of(
                new ShiftRecord("100", "Rider", "Operations", "Staff", date, LocalTime.of(9,0), LocalTime.of(17,30), "Gate"),
                new ShiftRecord("200", "Late rider", "Operations", "Staff", date, LocalTime.of(9,0), LocalTime.of(20,0), "Gate"));
        var morning = new technology.renatus.routepulse.scheduling.planning.MorningDrivingPlanningService(provider, "09:00", "12:00", 15, 15);
        var forecast = new technology.renatus.routepulse.scheduling.planning.TransportationDemandService(
                provider, morning, "09:00", "12:00", "17:00", "21:00", 15, 15).getDemandForDate(1L, DAY);
        equal(LocalTime.of(17,30), forecast.getEvening().getRecommendedStartTime(), "forecast recommends actual first demand, not early buffer");
        equal(LocalTime.of(20,30), forecast.getEvening().getRecommendedEndTime(), "pickup end buffer remains");
        equal(LocalTime.of(20,0), EveningClosingPolicy.afterClosing(forecast.getEvening(), LocalTime.of(19,0)).getRecommendedStartTime(),
                "real forecast waits through closing-time quiet gap");
    }
    private static TransportationDemandPeriod period(LocalTime from, LocalTime to) {
        return new TransportationDemandPeriod("evening","Evening","Evening","","","","",
                List.of(new TransportationDemandWindow(from,to,0,20,20,null)),from,to,20,from,to,20);
    }
    private static void equal(Object expected,Object actual,String message) {
        checks++; if (!Objects.equals(expected,actual)) throw new AssertionError(message+": expected "+expected+", got "+actual);
    }
    private static void rejects(Runnable action,String message) {
        checks++; try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError(message);
    }
    private static <T> T id(T object, Long id) {
        try { var field=object.getClass().getDeclaredField("id");field.setAccessible(true);field.set(object,id);return object; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,BiFunction<String,Object[],Object> answer) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->answer.apply(m.getName(),a));
    }
    private static final class Fixture {
        final List<WardrobeHoursRule> rows = new ArrayList<>();
        OrganizationRole role = OrganizationRole.ADMIN;
        final Organization org=id(new Organization("Test","Test","test",OrganizationStatus.ACTIVE,null,null),1L);
        final UserAccount actor=id(new UserAccount("manager@example.com","hash","Test","Manager",true,true),1L);
        final WardrobeHoursRepository repository=proxy(WardrobeHoursRepository.class,(method,args)-> {
            if(method.equals("save")) { var row=id((WardrobeHoursRule)args[0],(long)rows.size()+1);rows.add(row);return row; }
            if(method.equals("flush")) return null;
            return rows.stream().filter(r->r.getOrganizationId().equals(args[0]) && !r.getStartsOn().isAfter((LocalDate)args[1])
                    && !r.getEndsOn().isBefore((LocalDate)args[2])).toList();
        });
        final WardrobeHoursService service=new WardrobeHoursService(repository,
                proxy(UserAccountRepository.class,(m,a)->Optional.of(actor)),
                proxy(OrganizationMembershipRepository.class,(m,a)->a[0].equals(1L)
                        ? Optional.of(new OrganizationMembership(org,actor,role)):Optional.empty()),
                proxy(OrganizationRepository.class,(m,a)->Optional.of(org)),ZONE.getId());
        void save(String name,LocalDate from,LocalDate to,int hour) {
            service.save(1L,WardrobeHoursRule.Scope.SEASON,name,from,to,LocalTime.of(hour,0),actor.getEmail());
        }
    }
}
