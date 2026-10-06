package technology.renatus.routepulse.wardrobe;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;

@Service
@Transactional(readOnly = true)
public class WardrobeHoursService {
    public record Hours(LocalTime closingTime, String label, LocalDate startsOn, LocalDate endsOn,
                        boolean dailyOverride, LocalTime openingTime, Integer dayOfWeek) {
        public Hours(LocalTime closingTime, String label, LocalDate startsOn, LocalDate endsOn, boolean dailyOverride) {
            this(closingTime, label, startsOn, endsOn, dailyOverride, null, null);
        }
        public boolean usesOpeningWindow() { return openingTime != null; }
        public LocalTime openingStartTime() { return openingTime == null ? LocalTime.of(9, 0) : openingTime; }
        public LocalTime openingEndTime() {
            int closing = closingTime.equals(LocalTime.MIDNIGHT) ? 1440 : closingTime.toSecondOfDay() / 60;
            int end = Math.min(openingStartTime().toSecondOfDay() / 60 + 180, closing);
            // Assignments use same-day LocalTime windows; midnight closing remains the end of the day.
            return LocalTime.ofSecondOfDay(Math.min(end, 1439) * 60L);
        }
        public String weekdayLabel() { return dayOfWeek == null ? "Every day" : DayOfWeek.of(dayOfWeek)
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US) + "s"; }
    }
    public static final Hours DEFAULT = new Hours(LocalTime.of(19, 0), "Default hours", null, null, false);
    private final WardrobeHoursRepository rules;
    private final UserAccountRepository users;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final ZoneId zone;
    private final technology.renatus.routepulse.calendar.service.ParkOperatingDayService calendar;
    public WardrobeHoursService(WardrobeHoursRepository rules, UserAccountRepository users,
            OrganizationMembershipRepository memberships, OrganizationRepository organizations,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this(rules, users, memberships, organizations, null, zone);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public WardrobeHoursService(WardrobeHoursRepository rules, UserAccountRepository users,
            OrganizationMembershipRepository memberships, OrganizationRepository organizations,
            technology.renatus.routepulse.calendar.service.ParkOperatingDayService calendar,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.calendar = calendar;
        this.rules = rules; this.users = users; this.memberships = memberships;
        this.organizations = organizations; this.zone = ZoneId.of(zone);
    }
    public Hours getHours(Long organizationId, LocalDate date) {
        var hours = resolve(rules.findAllByOrganizationIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByCreatedAtDescIdDesc(
                organizationId, date, date), date, zone);
        return calendar == null ? hours : withCalendarOpening(hours, calendar.getOperatingDay(date));
    }
    /** Use verified park opening times; a day or weekday opening rule remains the manager's override. */
    public static Hours withCalendarOpening(Hours hours, technology.renatus.routepulse.calendar.domain.ParkOperatingDay day) {
        if ((hours.usesOpeningWindow() && (hours.dailyOverride() || hours.dayOfWeek() != null)) || day == null
                || day.status() != technology.renatus.routepulse.calendar.domain.ParkOperatingStatus.OPEN) return hours;
        LocalTime opening;
        String label;
        if (LocalTime.of(17, 0).equals(day.openingTime())) {
            opening = LocalTime.of(15, 0); label = "5 PM park opening";
        } else if (LocalTime.of(11, 0).equals(day.openingTime()) || LocalTime.NOON.equals(day.openingTime())) {
            opening = LocalTime.of(9, 0); label = "11 AM / noon park opening";
        } else return hours;
        // Do not create an invalid window when an explicit closing-only rule closes earlier.
        if (!hours.closingTime().equals(LocalTime.MIDNIGHT) && !hours.closingTime().isAfter(opening)) return hours;
        return new Hours(hours.closingTime(), hours.label() + " · " + label, hours.startsOn(), hours.endsOn(),
                hours.dailyOverride(), opening, hours.dayOfWeek());
    }
    /** A later seasonal edit cannot overwrite a day's explicit exception or rewrite earlier dates. */
    public static Hours resolve(List<WardrobeHoursRule> rules, LocalDate date, ZoneId zone) {
        var matching = rules.stream().filter(r -> !date.isBefore(r.getStartsOn()) && !date.isAfter(r.getEndsOn())
                && (r.getDayOfWeek() == null || r.getDayOfWeek() == date.getDayOfWeek().getValue())
                && r.getCreatedAt().isBefore(date.plusDays(1).atStartOfDay(zone).toInstant()))
                .sorted(Comparator.comparing(WardrobeHoursRule::getCreatedAt).reversed()
                        .thenComparing(WardrobeHoursRule::getId, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        var daily = matching.stream().filter(r -> r.getScope() == WardrobeHoursRule.Scope.DAY).findFirst();
        if (daily.isPresent() && daily.get().getClosingTime() != null) return view(daily.get());
        var seasons = matching.stream().filter(r -> r.getScope() == WardrobeHoursRule.Scope.SEASON).toList();
        return seasons.stream().filter(r -> r.getDayOfWeek() != null).findFirst()
                .or(() -> seasons.stream().findFirst()).map(WardrobeHoursService::view).orElse(DEFAULT);
    }
    private static Hours view(WardrobeHoursRule rule) {
        return new Hours(rule.getClosingTime(), rule.getLabel(), rule.getStartsOn(), rule.getEndsOn(),
                rule.getScope() == WardrobeHoursRule.Scope.DAY, rule.getOpeningTime(), rule.getDayOfWeek());
    }
    @Transactional
    public void save(Long organizationId, WardrobeHoursRule.Scope scope, String label, LocalDate from,
            LocalDate to, LocalTime closingTime, String actorEmail) {
        save(organizationId, scope, label, from, to, null, closingTime, null, actorEmail);
    }
    @Transactional
    public void save(Long organizationId, WardrobeHoursRule.Scope scope, String label, LocalDate from,
            LocalDate to, LocalTime openingTime, LocalTime closingTime, Integer dayOfWeek, String actorEmail) {
        if (openingTime != null && (closingTime == null || (!closingTime.equals(LocalTime.MIDNIGHT) && !closingTime.isAfter(openingTime))))
            throw new IllegalArgumentException("Wardrobe must close after it opens. Midnight means the end of the day.");
        if (openingTime != null && (openingTime.getSecond() != 0 || openingTime.getNano() != 0 || openingTime.isAfter(LocalTime.of(23, 58))))
            throw new IllegalArgumentException("Choose an opening time in whole minutes before 11:59 PM.");
        if (dayOfWeek != null && (dayOfWeek < 1 || dayOfWeek > 7 || scope != WardrobeHoursRule.Scope.SEASON))
            throw new IllegalArgumentException("Choose a valid weekday for a seasonal rule.");
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("Choose a valid date range.");
        if (to.isBefore(LocalDate.now(zone))) throw new IllegalArgumentException("Wardrobe hours can only be changed for today or future dates.");
        if (scope == null || (scope == WardrobeHoursRule.Scope.DAY && !from.equals(to)))
            throw new IllegalArgumentException("A daily override must apply to one date.");
        if (scope == WardrobeHoursRule.Scope.SEASON && closingTime == null)
            throw new IllegalArgumentException("A season needs a wardrobe closing time.");
        if (closingTime != null && (closingTime.getSecond() != 0 || closingTime.getNano() != 0))
            throw new IllegalArgumentException("Choose a closing time in whole minutes.");
        String name = label == null ? "" : label.trim();
        if (name.isBlank() || name.length() > 80) throw new IllegalArgumentException("Enter a season name or label of 80 characters or fewer.");
        var actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(() -> new IllegalStateException("Manager account was not found."));
        var member = memberships.findByOrganization_IdAndUser_Id(organizationId, actor.getId())
                .orElseThrow(() -> new IllegalStateException("You do not belong to this organization."));
        if (!Set.of(OrganizationRole.OWNER, OrganizationRole.ADMIN, OrganizationRole.DISPATCHER).contains(member.getRole()))
            throw new IllegalStateException("Only managers can change wardrobe hours.");
        organizations.findByIdForUpdate(organizationId).orElseThrow(() -> new IllegalStateException("Organization was not found."));
        rules.save(new WardrobeHoursRule(organizationId, scope, name, from, to, openingTime, closingTime, dayOfWeek, actor.getId(), Instant.now()));
        rules.flush();
    }
}
