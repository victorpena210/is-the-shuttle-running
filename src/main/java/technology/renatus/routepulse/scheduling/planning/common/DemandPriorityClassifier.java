package technology.renatus.routepulse.scheduling.planning.common;

import technology.renatus.routepulse.scheduling.domain.PickupPriority;

/** Shared demand thresholds used across pickup and transportation planning. */
public class DemandPriorityClassifier {

    private static final int MODERATE_DEMAND_MIN = 10;
    private static final int HIGH_DEMAND_MIN = 25;
    private static final int VERY_HIGH_DEMAND_MIN = 50;

    public PickupPriority classify(int count) {
        if (count >= VERY_HIGH_DEMAND_MIN) {
            return PickupPriority.VERY_HIGH;
        }
        if (count >= HIGH_DEMAND_MIN) {
            return PickupPriority.HIGH;
        }
        if (count >= MODERATE_DEMAND_MIN) {
            return PickupPriority.MEDIUM;
        }
        return PickupPriority.LOW;
    }
}
