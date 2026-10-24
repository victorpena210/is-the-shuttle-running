package technology.renatus.routepulse.scheduling.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Instant;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.organization.model.Organization;

@Entity
@Table(name = "schedule_imports")
public class ScheduleImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false,
            updatable = false
    )
    private Organization organization;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "uploaded_by_user_id",
            nullable = false,
            updatable = false
    )
    private UserAccount uploadedBy;

    @Column(
            name = "version_id",
            nullable = false,
            unique = true,
            length = 36,
            updatable = false
    )
    private String versionId;

    @Column(
            name = "original_filename",
            nullable = false,
            length = 255,
            updatable = false
    )
    private String originalFilename;

    @Column(
            name = "row_count",
            nullable = false,
            updatable = false
    )
    private int rowCount;

    @Column(
            name = "first_shift_date",
            nullable = false,
            updatable = false
    )
    private LocalDate firstShiftDate;

    @Column(
            name = "last_shift_date",
            nullable = false,
            updatable = false
    )
    private LocalDate lastShiftDate;

    @Column(
            name = "dates_represented",
            nullable = false,
            length = 2000,
            updatable = false
    )
    private String datesRepresented;

    @Column(
            name = "uploaded_at",
            nullable = false,
            updatable = false
    )
    private Instant uploadedAt;

    @jakarta.persistence.Transient
    private boolean weeklyDriverReport;
    public boolean isWeeklyDriverReport() { return weeklyDriverReport; }
    public void markWeeklyDriverReport() { weeklyDriverReport = true; }

    protected ScheduleImport() {
    }

    public ScheduleImport(
            Organization organization,
            UserAccount uploadedBy,
            String versionId,
            String originalFilename,
            int rowCount,
            LocalDate firstShiftDate,
            LocalDate lastShiftDate,
            String datesRepresented,
            Instant uploadedAt) {

        this.organization = organization;
        this.uploadedBy = uploadedBy;
        this.versionId = versionId;
        this.originalFilename = originalFilename;
        this.rowCount = rowCount;
        this.firstShiftDate = firstShiftDate;
        this.lastShiftDate = lastShiftDate;
        this.datesRepresented = datesRepresented;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UserAccount getUploadedBy() {
        return uploadedBy;
    }

    public String getVersionId() {
        return versionId;
    }

    public String getShortVersionId() {
        if (versionId == null || versionId.length() <= 8) {
            return versionId;
        }

        return versionId.substring(0, 8);
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public int getRowCount() {
        return rowCount;
    }

    public LocalDate getFirstShiftDate() {
        return firstShiftDate;
    }

    public LocalDate getLastShiftDate() {
        return lastShiftDate;
    }

    public String getDatesRepresented() {
        return datesRepresented;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getUploadedByDisplayName() {
        return uploadedBy.getFirstName()
                + " "
                + uploadedBy.getLastName();
    }
}
