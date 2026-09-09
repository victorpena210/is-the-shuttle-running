package technology.renatus.routepulse.organization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.organization.domain.OrganizationRole;

@Entity
@Table(
        name = "organization_invitations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name =
                                "uk_organization_invitations_org_email",
                        columnNames = {
                                "organization_id",
                                "invited_email"
                        }
                )
        }
)
public class OrganizationInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "invited_by_user_id",
            nullable = false
    )
    private UserAccount invitedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(
            name = "invited_email",
            nullable = false,
            length = 254
    )
    private String invitedEmail;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private OrganizationRole role;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    protected OrganizationInvitation() {
    }

    public OrganizationInvitation(
            Organization organization,
            UserAccount invitedBy,
            Driver driver,
            String invitedEmail,
            OrganizationRole role,
            String tokenHash,
            LocalDateTime expiresAt) {

        this.organization = organization;
        this.invitedBy = invitedBy;
        this.driver = driver;
        this.invitedEmail = invitedEmail;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UserAccount getInvitedBy() {
        return invitedBy;
    }

    public Driver getDriver() {
        return driver;
    }

    public String getInvitedEmail() {
        return invitedEmail;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public boolean isExpired(
            LocalDateTime now) {

        return !expiresAt.isAfter(now);
    }

    public void refresh(
            UserAccount invitedBy,
            Driver driver,
            OrganizationRole role,
            String tokenHash,
            LocalDateTime expiresAt) {

        this.invitedBy = invitedBy;
        this.driver = driver;
        this.role = role;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.acceptedAt = null;
    }

    public void markAccepted(
            LocalDateTime acceptedAt) {

        this.acceptedAt = acceptedAt;
    }
}
