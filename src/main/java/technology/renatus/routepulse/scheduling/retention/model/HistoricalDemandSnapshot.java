package technology.renatus.routepulse.scheduling.retention.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;

/**
 * Anonymous daily demand history retained after raw employee schedule rows
 * have been purged. This is the long-lived forecasting dataset; it deliberately
 * contains no employee names, employee IDs, departments, jobs, or locations.
 */
@Entity
@Table(
        name = "historical_demand_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_historical_demand_org_date",
                columnNames = {"organization_id", "operating_date"}
        )
)
public class HistoricalDemandSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_schedule_import_id")
    private ScheduleImport sourceScheduleImport;

    @Column(name = "operating_date", nullable = false, updatable = false)
    private LocalDate operatingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "shift_row_count", nullable = false)
    private int shiftRowCount;

    @Column(name = "employee_count", nullable = false)
    private int employeeCount;

    @Column(name = "morning_demand_count", nullable = false)
    private int morningDemandCount;

    @Column(name = "midday_demand_count", nullable = false)
    private int middayDemandCount;

    @Column(name = "evening_demand_count", nullable = false)
    private int eveningDemandCount;

    @Column(name = "off_hours_movement_count", nullable = false)
    private int offHoursMovementCount;

    @Column(name = "morning_recommended_start")
    private LocalTime morningRecommendedStart;

    @Column(name = "morning_recommended_end")
    private LocalTime morningRecommendedEnd;

    @Column(name = "overall_peak_window_start")
    private LocalTime overallPeakWindowStart;

    @Column(name = "overall_peak_window_end")
    private LocalTime overallPeakWindowEnd;

    @Column(name = "overall_peak_window_count", nullable = false)
    private int overallPeakWindowCount;

    @Column(name = "overall_peak_period_title", length = 80)
    private String overallPeakPeriodTitle;

    @Column(name = "snapshot_created_at", nullable = false)
    private Instant snapshotCreatedAt;

    @OneToMany(
            mappedBy = "snapshot",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("windowStart ASC")
    private List<HistoricalDemandWindow> windows = new ArrayList<>();

    protected HistoricalDemandSnapshot() {
    }

    public HistoricalDemandSnapshot(
            Organization organization,
            LocalDate operatingDate) {
        this.organization = organization;
        this.operatingDate = operatingDate;
        this.dayOfWeek = operatingDate.getDayOfWeek();
    }

    public void refresh(
            ScheduleImport sourceScheduleImport,
            int shiftRowCount,
            int employeeCount,
            int morningDemandCount,
            int middayDemandCount,
            int eveningDemandCount,
            int offHoursMovementCount,
            LocalTime morningRecommendedStart,
            LocalTime morningRecommendedEnd,
            LocalTime overallPeakWindowStart,
            LocalTime overallPeakWindowEnd,
            int overallPeakWindowCount,
            String overallPeakPeriodTitle,
            Instant snapshotCreatedAt) {

        this.sourceScheduleImport = sourceScheduleImport;
        this.dayOfWeek = operatingDate.getDayOfWeek();
        this.shiftRowCount = shiftRowCount;
        this.employeeCount = employeeCount;
        this.morningDemandCount = morningDemandCount;
        this.middayDemandCount = middayDemandCount;
        this.eveningDemandCount = eveningDemandCount;
        this.offHoursMovementCount = offHoursMovementCount;
        this.morningRecommendedStart = morningRecommendedStart;
        this.morningRecommendedEnd = morningRecommendedEnd;
        this.overallPeakWindowStart = overallPeakWindowStart;
        this.overallPeakWindowEnd = overallPeakWindowEnd;
        this.overallPeakWindowCount = overallPeakWindowCount;
        this.overallPeakPeriodTitle = overallPeakPeriodTitle;
        this.snapshotCreatedAt = snapshotCreatedAt;
    }

    public void replaceWindows(List<HistoricalDemandWindow> replacementWindows) {
        Map<LocalTime, HistoricalDemandWindow> replacementsByStart =
                new LinkedHashMap<>();
        replacementWindows.forEach(window ->
                replacementsByStart.put(window.getWindowStart(), window)
        );

        windows.removeIf(existing ->
                !replacementsByStart.containsKey(existing.getWindowStart())
        );

        Map<LocalTime, HistoricalDemandWindow> existingByStart =
                new LinkedHashMap<>();
        windows.forEach(existing ->
                existingByStart.put(existing.getWindowStart(), existing)
        );

        replacementWindows.forEach(replacement -> {
            HistoricalDemandWindow existing = existingByStart.get(
                    replacement.getWindowStart()
            );

            if (existing != null) {
                existing.refresh(
                        replacement.getWindowEnd(),
                        replacement.getArrivals(),
                        replacement.getDepartures()
                );
                return;
            }

            replacement.attachTo(this);
            windows.add(replacement);
        });

        windows.sort(Comparator.comparing(HistoricalDemandWindow::getWindowStart));
    }

    public Long getId() { return id; }
    public Organization getOrganization() { return organization; }
    public ScheduleImport getSourceScheduleImport() { return sourceScheduleImport; }
    public LocalDate getOperatingDate() { return operatingDate; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public int getShiftRowCount() { return shiftRowCount; }
    public int getEmployeeCount() { return employeeCount; }
    public int getMorningDemandCount() { return morningDemandCount; }
    public int getMiddayDemandCount() { return middayDemandCount; }
    public int getEveningDemandCount() { return eveningDemandCount; }
    public int getOffHoursMovementCount() { return offHoursMovementCount; }
    public LocalTime getMorningRecommendedStart() { return morningRecommendedStart; }
    public LocalTime getMorningRecommendedEnd() { return morningRecommendedEnd; }
    public LocalTime getOverallPeakWindowStart() { return overallPeakWindowStart; }
    public LocalTime getOverallPeakWindowEnd() { return overallPeakWindowEnd; }
    public int getOverallPeakWindowCount() { return overallPeakWindowCount; }
    public String getOverallPeakPeriodTitle() { return overallPeakPeriodTitle; }
    public Instant getSnapshotCreatedAt() { return snapshotCreatedAt; }
    public List<HistoricalDemandWindow> getWindows() { return List.copyOf(windows); }
}
