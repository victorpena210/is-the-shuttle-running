package technology.renatus.routepulse.calendar;

import org.springframework.web.client.RestClientException;

public class SixFlagsCalendarException
        extends RestClientException {

    private final String diagnosticCode;

    public SixFlagsCalendarException(
            String diagnosticCode,
            String message,
            Throwable cause) {

        super(message, cause);
        this.diagnosticCode = diagnosticCode;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }
}
