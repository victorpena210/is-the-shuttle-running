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
import java.time.Instant;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.organization.domain.OrganizationRole;

@Entity
@Table(
        name = "organization_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_organization_memberships_org_user",
                        columnNames = {
                                "organization_id",
                                "user_id"
                        }
                )
        }
)
public class OrganizationMembership {

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
            name = "user_id",
            nullable = false
    )
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private OrganizationRole role;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    protected OrganizationMembership() {
    }

    public OrganizationMembership(
            Organization organization,
            UserAccount user,
            OrganizationRole role) {

        this.organization = organization;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UserAccount getUser() {
        return user;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changeRole(OrganizationRole role) {
        this.role = role;
    }
}
