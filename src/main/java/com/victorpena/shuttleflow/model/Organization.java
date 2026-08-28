package com.victorpena.shuttleflow.model;

import java.time.LocalDateTime;

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
    private LocalDateTime trialStartedAt;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    protected Organization() {
    }

    public Organization(
            String legalName,
            String displayName,
            String slug,
            OrganizationStatus status,
            LocalDateTime trialStartedAt,
            LocalDateTime trialEndsAt) {

        this.legalName = legalName;
        this.displayName = displayName;
        this.slug = slug;
        this.status = status;
        this.trialStartedAt = trialStartedAt;
        this.trialEndsAt = trialEndsAt;
    }

    @PrePersist
    void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {

        updatedAt = LocalDateTime.now();
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

    public LocalDateTime getTrialStartedAt() {
        return trialStartedAt;
    }

    public LocalDateTime getTrialEndsAt() {
        return trialEndsAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public boolean hasApplicationAccess(
            LocalDateTime now) {

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