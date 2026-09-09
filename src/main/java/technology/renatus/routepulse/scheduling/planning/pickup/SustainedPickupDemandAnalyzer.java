package technology.renatus.routepulse.scheduling.planning.pickup;

import java.util.ArrayList;
import java.util.List;
import technology.renatus.routepulse.scheduling.domain.PickupWindow;

/** Finds sustained pickup-demand stretches and marks their windows. */
public class SustainedPickupDemandAnalyzer {

    private static final int SUSTAINED_DEMAND_MIN = 10;
    private static final int SUSTAINED_WINDOW_MIN = 3;

    /**
     * Marks every sustained-demand window and returns the busiest sustained cluster.
     * An empty list means no cluster qualified.
     */
    public List<PickupWindow> findPeakClusterAndMark(List<PickupWindow> windows) {
        List<List<PickupWindow>> clusters = findSustainedDemandClusters(windows);
        clusters.forEach(cluster -> cluster.forEach(window -> window.setSustainedDemand(true)));
        return findPeakCluster(clusters);
    }

    private List<List<PickupWindow>> findSustainedDemandClusters(List<PickupWindow> windows) {
        List<List<PickupWindow>> clusters = new ArrayList<>();
        List<PickupWindow> currentCluster = new ArrayList<>();

        for (PickupWindow window : windows) {
            if (window.getTeamMemberCount() < SUSTAINED_DEMAND_MIN) {
                saveClusterIfQualified(clusters, currentCluster);
                currentCluster = new ArrayList<>();
                continue;
            }

            if (currentCluster.isEmpty()) {
                currentCluster.add(window);
                continue;
            }

            PickupWindow previousWindow = currentCluster.get(currentCluster.size() - 1);
            boolean consecutive = previousWindow.getWindowStart()
                    .plusMinutes(PickupWindowCalculator.WINDOW_MINUTES)
                    .equals(window.getWindowStart());

            if (consecutive) {
                currentCluster.add(window);
            } else {
                saveClusterIfQualified(clusters, currentCluster);
                currentCluster = new ArrayList<>();
                currentCluster.add(window);
            }
        }

        saveClusterIfQualified(clusters, currentCluster);
        return clusters;
    }

    private void saveClusterIfQualified(
            List<List<PickupWindow>> clusters,
            List<PickupWindow> candidate) {
        if (candidate.size() >= SUSTAINED_WINDOW_MIN) {
            clusters.add(List.copyOf(candidate));
        }
    }

    private List<PickupWindow> findPeakCluster(List<List<PickupWindow>> clusters) {
        List<PickupWindow> peakCluster = List.of();
        int highestTotal = -1;

        for (List<PickupWindow> cluster : clusters) {
            int clusterTotal = cluster.stream()
                    .mapToInt(PickupWindow::getTeamMemberCount)
                    .sum();

            if (clusterTotal > highestTotal) {
                highestTotal = clusterTotal;
                peakCluster = cluster;
            }
        }

        return peakCluster;
    }
}
