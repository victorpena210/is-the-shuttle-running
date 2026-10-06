package technology.renatus.routepulse.organization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;

@Entity
@Table(name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "legal_name",
            nullable = false,
            length = 200
    )
    private String legalName;

    @Column(
            name = "display_name",
            nullable = false,
            length = 150
    )
    private String displayName;

    @Column(
            nullable = false,
            unique = true,
            length = 120
    )
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private OrganizationStatus status;

    @Column(name = "trial_started_at")
    private Instant trialStartedAt;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(
            name = "driving_qr_code",
            nullable = false,
            unique = true,
            length = 64
    )
    private String drivingQrCode;

    @Column(name = "rider_pickup_instructions", length = 1500)
    private String riderPickupInstructions;

    @Column(name = "rider_help_contact", length = 300)
    private String riderHelpContact;

    public String getRiderPickupInstructions() { return riderPickupInstructions; }
    public String getRiderHelpContact() { return riderHelpContact; }
    public void updateRiderInformation(String pickupInstructions, String helpContact) {
        riderPickupInstructions = normalizeRiderText(pickupInstructions, 1500);
        riderHelpContact = normalizeRiderText(helpContact, 300);
    }
    private static String normalizeRiderText(String value, int max) {
        String result = value == null || value.isBlank() ? null : value.trim();
        if (result != null && result.length() > max)
            throw new IllegalArgumentException("Rider information exceeds the " + max + " character limit.");
        return result;
    }

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected Organization() {
    }

    public Organization(
            String legalName,
            String displayName,
            String slug,
            OrganizationStatus status,
            Instant trialStartedAt,
            Instant trialEndsAt) {

        this.legalName = legalName;
        this.displayName = displayName;
        this.slug = slug;
        this.status = status;
        this.trialStartedAt = trialStartedAt;
        this.trialEndsAt = trialEndsAt;
    }

    @PrePersist
    void onCreate() {

        Instant now = Instant.now();

        if (drivingQrCode == null
                || drivingQrCode.isBlank()) {

            drivingQrCode =
                    generateDrivingQrCode();
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void regenerateDrivingQrCode() {
        drivingQrCode = generateDrivingQrCode();
    }

    private static String generateDrivingQrCode() {

        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toLowerCase();
    }

    public Long getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSlug() {
        return slug;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public Instant getTrialStartedAt() {
        return trialStartedAt;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public String getDrivingQrCode() {
        return drivingQrCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasApplicationAccess(
            Instant now) {

        if (status == OrganizationStatus.ACTIVE) {
            return true;
        }

        if (status == OrganizationStatus.TRIAL) {
            return trialEndsAt != null
                    && now.isBefore(trialEndsAt);
        }

        return false;
    }
}
