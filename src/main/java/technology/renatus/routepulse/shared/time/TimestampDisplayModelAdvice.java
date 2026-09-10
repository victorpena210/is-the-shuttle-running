package technology.renatus.routepulse.shared.time;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the UTC-to-operations-zone formatter available to every Thymeleaf view. */
@ControllerAdvice
public class TimestampDisplayModelAdvice {

    private final TimestampDisplay timestampDisplay;

    public TimestampDisplayModelAdvice(TimestampDisplay timestampDisplay) {
        this.timestampDisplay = timestampDisplay;
    }

    @ModelAttribute("timestampDisplay")
    public TimestampDisplay timestampDisplay() {
        return timestampDisplay;
    }
}
