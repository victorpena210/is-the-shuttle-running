package technology.renatus.routepulse.operations.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import technology.renatus.routepulse.operations.domain.OperationalStatusOverride;

@Entity
@Table(name = "operational_status_updates")
public class OperationalStatusUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_override", nullable = false, length = 40)
    private OperationalStatusOverride statusOverride;

    @Column(name = "operational_message", length = 500)
    private String operationalMessage;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "updated_by_name", nullable = false, length = 220)
    private String updatedByName;

    @Column(name = "updated_by_email", nullable = false, length = 254)
    private String updatedByEmail;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected OperationalStatusUpdate() {
    }

    public OperationalStatusUpdate(
            Long organizationId,
            OperationalStatusOverride statusOverride,
            String operationalMessage,
            Long updatedByUserId,
            String updatedByName,
            String updatedByEmail,
            Instant updatedAt) {
        this.organizationId = organizationId;
        this.statusOverride = statusOverride;
        this.operationalMessage = operationalMessage;
        this.updatedByUserId = updatedByUserId;
        this.updatedByName = updatedByName;
        this.updatedByEmail = updatedByEmail;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public OperationalStatusOverride getStatusOverride() { return statusOverride; }
    public String getOperationalMessage() { return operationalMessage; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public String getUpdatedByName() { return updatedByName; }
    public String getUpdatedByEmail() { return updatedByEmail; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() {
        // Older records also expire; a deployment cannot revive an old pause.
        return expiresAt == null ? updatedAt.plusSeconds(120 * 60L) : expiresAt;
    }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isExpired(Instant now) { return !now.isBefore(getExpiresAt()); }
}
