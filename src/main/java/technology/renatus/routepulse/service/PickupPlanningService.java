package technology.renatus.routepulse.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import technology.renatus.routepulse.model.PickupDemandAnalysis;
import technology.renatus.routepulse.model.PickupDemandSection;
import technology.renatus.routepulse.model.PickupPriority;
import technology.renatus.routepulse.model.PickupWindow;
import technology.renatus.routepulse.model.ShiftRecord;
import technology.renatus.routepulse.provider.ScheduleProvider;

@Service
public class PickupPlanningService {

    private static final int WINDOW_MINUTES = 15;

    /*
     * Demand levels.
     *
     * Low:
     * 0 - 9
     *
     * Moderate:
     * 10 - 24
     *
     * High:
     * 25 - 49
     *
     * Very High:
     * 50+
     */
    private static final int
            MODERATE_DEMAND_MIN = 10;

    private static final int
            HIGH_DEMAND_MIN = 25;

    private static final int
            VERY_HIGH_DEMAND_MIN = 50;

    /*
     * Sustained demand requires:
     *
     * 3 or more consecutive
     * 15-minute windows
     *
     * with at least 10 team
     * members in every window.
     */
    private static final int
            SUSTAINED_DEMAND_MIN = 10;

    private static final int
            SUSTAINED_WINDOW_MIN = 3;

    private final ScheduleProvider
            scheduleProvider;

    public PickupPlanningService(
            ScheduleProvider scheduleProvider) {

        this.scheduleProvider =
                scheduleProvider;
    }

    public PickupDemandAnalysis
            getPickupDemandForDate(
                    Long organizationId,
                    LocalDate date) {

        List<ShiftRecord> shifts =
                scheduleProvider
                        .getSchedulesForDate(
                                organizationId,
                                date
                        );

        Map<LocalTime, Long> shiftCounts =
                shifts.stream()
                        .collect(
                                Collectors.groupingBy(
                                        shift ->
                                                calculateWindowStart(
                                                        shift.getEndTime()
                                                ),
                                        Collectors.counting()
                                )
                        );

        List<PickupWindow> windows =
                shiftCounts
                        .entrySet()
                        .stream()
                        .sorted(
                                Map.Entry
                                        .comparingByKey()
                        )
                        .map(entry ->
                                createPickupWindow(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                        .toList();

        /*
         * Find busy stretches such as:
         *
         * 6:00
         * 6:15
         * 6:30
         * 6:45
         * 7:00
         */
        List<List<PickupWindow>> clusters =
                findSustainedDemandClusters(
                        windows
                );

        /*
         * Mark every window that belongs
         * to one of those clusters.
         */
        clusters.forEach(cluster ->

                cluster.forEach(window ->

                        window.setSustainedDemand(
                                true
                        )
                )
        );

        /*
         * If multiple clusters exist,
         * choose the one containing the
         * largest total number of people.
         */
        List<PickupWindow> peakCluster =
                findPeakCluster(
                        clusters
                );

        int peakWindowTeamMemberCount =
                windows.stream()
                        .mapToInt(
                                PickupWindow::
                                        getTeamMemberCount
                        )
                        .max()
                        .orElse(0);

        LocalTime peakDemandStart =
                null;

        LocalTime peakDemandEnd =
                null;

        int peakDemandTeamMemberCount =
                0;

        int sustainedWindowCount =
                0;

        if (!peakCluster.isEmpty()) {

            peakDemandStart =
                    peakCluster
                            .get(0)
                            .getWindowStart();

            peakDemandEnd =
                    peakCluster
                            .get(
                                    peakCluster.size()
                                            - 1
                            )
                            .getWindowEnd();

            peakDemandTeamMemberCount =
                    peakCluster.stream()
                            .mapToInt(
                                    PickupWindow::
                                            getTeamMemberCount
                            )
                            .sum();

            sustainedWindowCount =
                    peakCluster.size();
        }

        return new PickupDemandAnalysis(
                windows,
                buildSections(
                        windows
                ),
                peakDemandStart,
                peakDemandEnd,
                peakDemandTeamMemberCount,
                peakWindowTeamMemberCount,
                sustainedWindowCount
        );
    }

    /*
     * Keep the original method available
     * in case another part of RoutePulse
     * still asks only for pickup windows.
     */
    public List<PickupWindow>
            getPickupWindowsForDate(
                    Long organizationId,
                    LocalDate date) {

        return getPickupDemandForDate(
                organizationId,
                date
        ).getWindows();
    }

    private PickupWindow createPickupWindow(
            LocalTime windowStart,
            long teamMemberCount) {

        int count =
                Math.toIntExact(
                        teamMemberCount
                );

        return new PickupWindow(
                windowStart,
                windowStart.plusMinutes(
                        WINDOW_MINUTES
                ),
                count,
                determinePriority(
                        count
                )
        );
    }

    private LocalTime calculateWindowStart(
            LocalTime endTime) {

        int roundedMinute =
                (
                        endTime.getMinute()
                                / WINDOW_MINUTES
                )
                        * WINDOW_MINUTES;

        return endTime
                .withMinute(
                        roundedMinute
                )
                .withSecond(0)
                .withNano(0);
    }

    private PickupPriority determinePriority(
            int count) {

        if (count >=
                VERY_HIGH_DEMAND_MIN) {

            return PickupPriority
                    .VERY_HIGH;
        }

        if (count >=
                HIGH_DEMAND_MIN) {

            return PickupPriority
                    .HIGH;
        }

        if (count >=
                MODERATE_DEMAND_MIN) {

            return PickupPriority
                    .MEDIUM;
        }

        return PickupPriority
                .LOW;
    }

    private List<List<PickupWindow>>
            findSustainedDemandClusters(
                    List<PickupWindow>
                            windows) {

        List<List<PickupWindow>>
                clusters =
                        new ArrayList<>();

        List<PickupWindow>
                currentCluster =
                        new ArrayList<>();

        for (PickupWindow window :
                windows) {

            boolean enoughDemand =
                    window
                            .getTeamMemberCount()
                            >=
                            SUSTAINED_DEMAND_MIN;

            /*
             * A low-demand window ends
             * the current cluster.
             */
            if (!enoughDemand) {

                saveClusterIfQualified(
                        clusters,
                        currentCluster
                );

                currentCluster =
                        new ArrayList<>();

                continue;
            }

            /*
             * First busy window.
             */
            if (currentCluster.isEmpty()) {

                currentCluster.add(
                        window
                );

                continue;
            }

            PickupWindow previousWindow =
                    currentCluster.get(
                            currentCluster.size()
                                    - 1
                    );

            /*
             * The next window must begin
             * exactly 15 minutes later.
             */
            boolean consecutive =
                    previousWindow
                            .getWindowStart()
                            .plusMinutes(
                                    WINDOW_MINUTES
                            )
                            .equals(
                                    window
                                            .getWindowStart()
                            );

            if (consecutive) {

                currentCluster.add(
                        window
                );

            } else {

                saveClusterIfQualified(
                        clusters,
                        currentCluster
                );

                currentCluster =
                        new ArrayList<>();

                currentCluster.add(
                        window
                );
            }
        }

        /*
         * Save the final cluster,
         * if one is still open.
         */
        saveClusterIfQualified(
                clusters,
                currentCluster
        );

        return clusters;
    }

    private void saveClusterIfQualified(
            List<List<PickupWindow>>
                    clusters,
            List<PickupWindow>
                    candidate) {

        if (candidate.size()
                >=
                SUSTAINED_WINDOW_MIN) {

            clusters.add(
                    List.copyOf(
                            candidate
                    )
            );
        }
    }

    private List<PickupWindow>
            findPeakCluster(
                    List<List<PickupWindow>>
                            clusters) {

        List<PickupWindow> peakCluster =
                List.of();

        int highestTotal =
                -1;

        for (List<PickupWindow> cluster :
                clusters) {

            int clusterTotal =
                    cluster.stream()
                            .mapToInt(
                                    PickupWindow::
                                            getTeamMemberCount
                            )
                            .sum();

            if (clusterTotal
                    > highestTotal) {

                highestTotal =
                        clusterTotal;

                peakCluster =
                        cluster;
            }
        }

        return peakCluster;
    }

    /*
     * Divide the vertical timeline into
     * understandable portions of the day.
     */
    private List<PickupDemandSection>
            buildSections(
                    List<PickupWindow>
                            windows) {

        List<PickupDemandSection>
                sections =
                        new ArrayList<>();

        addSection(
                sections,
                "Overnight",
                "12:00 AM – 5:59 AM",
                "☾",
                windowsInHourRange(
                        windows,
                        0,
                        6
                )
        );

        addSection(
                sections,
                "Morning",
                "6:00 AM – 11:59 AM",
                "☀",
                windowsInHourRange(
                        windows,
                        6,
                        12
                )
        );

        addSection(
                sections,
                "Afternoon",
                "12:00 PM – 5:59 PM",
                "◐",
                windowsInHourRange(
                        windows,
                        12,
                        18
                )
        );

        addSection(
                sections,
                "Evening",
                "6:00 PM – 11:59 PM",
                "☾",
                windowsInHourRange(
                        windows,
                        18,
                        24
                )
        );

        return sections;
    }

    private List<PickupWindow>
            windowsInHourRange(
                    List<PickupWindow>
                            windows,
                    int startHourInclusive,
                    int endHourExclusive) {

        return windows.stream()
                .filter(window -> {

                    int hour =
                            window
                                    .getWindowStart()
                                    .getHour();

                    return hour
                            >= startHourInclusive

                            && hour
                            < endHourExclusive;
                })
                .toList();
    }

    private void addSection(
            List<PickupDemandSection>
                    sections,
            String name,
            String timeRange,
            String icon,
            List<PickupWindow>
                    windows) {

        /*
         * Don't show an empty
         * daypart section.
         */
        if (windows.isEmpty()) {
            return;
        }

        boolean sustainedDemand =
                windows.stream()
                        .anyMatch(
                                PickupWindow::
                                        isSustainedDemand
                        );

        sections.add(
                new PickupDemandSection(
                        name,
                        timeRange,
                        icon,
                        windows,
                        sustainedDemand
                )
        );
    }
}