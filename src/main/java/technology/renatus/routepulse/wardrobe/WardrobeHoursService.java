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
                        boolean dailyOverride) {}
    public static final Hours DEFAULT = new Hours(LocalTime.of(19, 0), "Default hours", null, null, false);
    private final WardrobeHoursRepository rules;
    private final UserAccountRepository users;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final ZoneId zone;
    public WardrobeHoursService(WardrobeHoursRepository rules, UserAccountRepository users,
            OrganizationMembershipRepository memberships, OrganizationRepository organizations,
            @Value("${app.operations-zone:America/Chicago}") String zone) {
        this.rules = rules; this.users = users; this.memberships = memberships;
        this.organizations = organizations; this.zone = ZoneId.of(zone);
    }
    public Hours getHours(Long organizationId, LocalDate date) {
        return resolve(rules.findAllByOrganizationIdAndStartsOnLessThanEqualAndEndsOnGreaterThanEqualOrderByCreatedAtDescIdDesc(
                organizationId, date, date), date, zone);
    }
    /** A later seasonal edit cannot overwrite a day's explicit exception or rewrite earlier dates. */
    public static Hours resolve(List<WardrobeHoursRule> rules, LocalDate date, ZoneId zone) {
        var matching = rules.stream().filter(r -> !date.isBefore(r.getStartsOn()) && !date.isAfter(r.getEndsOn())
                && r.getCreatedAt().isBefore(date.plusDays(1).atStartOfDay(zone).toInstant()))
                .sorted(Comparator.comparing(WardrobeHoursRule::getCreatedAt).reversed()
                        .thenComparing(WardrobeHoursRule::getId, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        var daily = matching.stream().filter(r -> r.getScope() == WardrobeHoursRule.Scope.DAY).findFirst();
        if (daily.isPresent() && daily.get().getClosingTime() != null) return view(daily.get());
        return matching.stream().filter(r -> r.getScope() == WardrobeHoursRule.Scope.SEASON)
                .findFirst().map(WardrobeHoursService::view).orElse(DEFAULT);
    }
    private static Hours view(WardrobeHoursRule rule) {
        return new Hours(rule.getClosingTime(), rule.getLabel(), rule.getStartsOn(), rule.getEndsOn(),
                rule.getScope() == WardrobeHoursRule.Scope.DAY);
    }
    @Transactional
    public void save(Long organizationId, WardrobeHoursRule.Scope scope, String label, LocalDate from,
            LocalDate to, LocalTime closingTime, String actorEmail) {
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
        rules.save(new WardrobeHoursRule(organizationId, scope, name, from, to, closingTime, actor.getId(), Instant.now()));
        rules.flush();
    }
}
