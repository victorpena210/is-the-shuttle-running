package technology.renatus.routepulse.driving.service;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class DrivingSessionReviewTimeTests {
    @Test void repeatedClockTimesRequireTheSelectedOccurrenceAndSpringGapIsRejected() {
        var zone = ZoneId.of("America/Chicago");
        assertThat(DrivingSessionReviewService.resolveTime("2026-11-01T01:30", "earlier", zone))
                .isEqualTo(Instant.parse("2026-11-01T06:30:00Z"));
        assertThat(DrivingSessionReviewService.resolveTime("2026-11-01T01:30", "later", zone))
                .isEqualTo(Instant.parse("2026-11-01T07:30:00Z"));
        assertThatThrownBy(() -> DrivingSessionReviewService.resolveTime("2026-03-08T02:30", "earlier", zone))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not exist");
    }
}
