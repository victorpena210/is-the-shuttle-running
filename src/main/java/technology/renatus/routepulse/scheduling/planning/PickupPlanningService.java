package technology.renatus.routepulse.scheduling.planning;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import technology.renatus.routepulse.scheduling.domain.PickupDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.PickupWindow;
import technology.renatus.routepulse.scheduling.domain.ShiftRecord;
import technology.renatus.routepulse.scheduling.importing.ScheduleProvider;
import technology.renatus.routepulse.scheduling.planning.common.DemandPriorityClassifier;
import technology.renatus.routepulse.scheduling.planning.pickup.PickupDemandSectionBuilder;
import technology.renatus.routepulse.scheduling.planning.pickup.PickupWindowCalculator;
import technology.renatus.routepulse.scheduling.planning.pickup.SustainedPickupDemandAnalyzer;

/** Orchestrates pickup-demand planning while delegating calculation details. */
@Service
public class PickupPlanningService {

    private final ScheduleProvider scheduleProvider;
    private final PickupWindowCalculator windowCalculator;
    private final SustainedPickupDemandAnalyzer sustainedDemandAnalyzer;
    private final PickupDemandSectionBuilder sectionBuilder;

    public PickupPlanningService(ScheduleProvider scheduleProvider) {
        this.scheduleProvider = scheduleProvider;
        DemandPriorityClassifier priorityClassifier = new DemandPriorityClassifier();
        this.windowCalculator = new PickupWindowCalculator(priorityClassifier);
        this.sustainedDemandAnalyzer = new SustainedPickupDemandAnalyzer();
        this.sectionBuilder = new PickupDemandSectionBuilder();
    }

    public PickupDemandAnalysis getPickupDemandForDate(Long organizationId, LocalDate date) {
        List<ShiftRecord> shifts = scheduleProvider.getSchedulesForDate(organizationId, date);
        List<PickupWindow> windows = windowCalculator.createWindows(shifts);
        List<PickupWindow> peakCluster = sustainedDemandAnalyzer.findPeakClusterAndMark(windows);

        int peakWindowTeamMemberCount = windows.stream()
                .mapToInt(PickupWindow::getTeamMemberCount)
                .max()
                .orElse(0);

        LocalTime peakDemandStart = peakCluster.isEmpty()
                ? null
                : peakCluster.get(0).getWindowStart();
        LocalTime peakDemandEnd = peakCluster.isEmpty()
                ? null
                : peakCluster.get(peakCluster.size() - 1).getWindowEnd();
        int peakDemandTeamMemberCount = peakCluster.stream()
                .mapToInt(PickupWindow::getTeamMemberCount)
                .sum();

        return new PickupDemandAnalysis(
                windows,
                sectionBuilder.build(windows),
                peakDemandStart,
                peakDemandEnd,
                peakDemandTeamMemberCount,
                peakWindowTeamMemberCount,
                peakCluster.size()
        );
    }

    /** Keeps the original window-only API for callers that do not need the analysis summary. */
    public List<PickupWindow> getPickupWindowsForDate(Long organizationId, LocalDate date) {
        return getPickupDemandForDate(organizationId, date).getWindows();
    }
}
