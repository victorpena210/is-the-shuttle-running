package technology.renatus.routepulse.wardrobe;

import jakarta.persistence.*;
import java.time.*;

/** Immutable history of a manager's seasonal hours or one-day exception. */
@Entity
@Table(name = "wardrobe_hours_rules")
public class WardrobeHoursRule {
    public enum Scope { SEASON, DAY }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false) private Long organizationId;
    @Enumerated(EnumType.STRING) @Column(name = "rule_scope", nullable = false, length = 10) private Scope scope;
    @Column(nullable = false, length = 80) private String label;
    @Column(name = "starts_on", nullable = false) private LocalDate startsOn;
    @Column(name = "ends_on", nullable = false) private LocalDate endsOn;
    // A DAY row with no time resets that day's override to the seasonal rule.
    @Column(name = "closing_time") private LocalTime closingTime;
    @Column(name = "changed_by_user_id", nullable = false) private Long changedByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected WardrobeHoursRule() {}
    public WardrobeHoursRule(Long organizationId, Scope scope, String label, LocalDate startsOn,
            LocalDate endsOn, LocalTime closingTime, Long changedByUserId, Instant createdAt) {
        this.organizationId = organizationId; this.scope = scope; this.label = label;
        this.startsOn = startsOn; this.endsOn = endsOn; this.closingTime = closingTime;
        this.changedByUserId = changedByUserId; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Scope getScope() { return scope; }
    public String getLabel() { return label; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public LocalTime getClosingTime() { return closingTime; }
    public Long getChangedByUserId() { return changedByUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
