package technology.renatus.routepulse.driving.service.rider;

import org.junit.jupiter.api.Test;

class PublicDriverNameTests {
    @Test
    void publicNamesOmitSurnamesAndMiddleInitials() {
        PublicDriverNameChecks.verify();
    }
}
