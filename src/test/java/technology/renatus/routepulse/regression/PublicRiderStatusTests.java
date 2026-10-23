package technology.renatus.routepulse.regression;

import org.junit.jupiter.api.Test;

class PublicRiderStatusTests {
    @Test
    void publicPageShowsOnlyBinaryStatusAndDriverWithoutPassengerConfirmation() {
        PublicRiderStatusChecks.verify();
    }
}
