package technology.renatus.routepulse.calendar;

import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SixFlagsCalendarClient {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    SixFlagsCalendarClient.class
            );

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMM");

    private final RestClient primaryClient;
    private final RestClient fallbackClient;
    private final String primaryBaseUrl;
    private final String fallbackBaseUrl;
    private final int parkId;

    public SixFlagsCalendarClient(
            RestClient.Builder restClientBuilder,
            @Value("${sixflags.calendar.base-url}")
            String primaryBaseUrl,
            @Value("${sixflags.calendar.fallback-base-url}")
            String fallbackBaseUrl,
            @Value("${sixflags.calendar.park-id}")
            int parkId) {

        this.primaryBaseUrl = primaryBaseUrl;
        this.fallbackBaseUrl = fallbackBaseUrl;
        this.parkId = parkId;

        this.primaryClient = restClientBuilder
                .clone()
                .requestFactory(createRequestFactory())
                .baseUrl(primaryBaseUrl)
                .build();

        this.fallbackClient = restClientBuilder
                .clone()
                .requestFactory(createRequestFactory())
                .baseUrl(fallbackBaseUrl)
                .build();
    }

    public SixFlagsCalendarResponse getCalendarForMonth(
            LocalDate date) {

        String month = date.format(MONTH_FORMAT);

        SixFlagsCalendarException primaryFailure;

        try {
            SixFlagsCalendarResponse response =
                    fetch(
                            primaryClient,
                            primaryBaseUrl,
                            month
                    );

            LOGGER.info(
                    "Six Flags calendar loaded from primary endpoint "
                            + "for park {} month {}",
                    parkId,
                    month
            );

            return response;

        } catch (SixFlagsCalendarException exception) {

            primaryFailure = exception;

            LOGGER.warn(
                    "Six Flags primary calendar endpoint failed "
                            + "for park {} month {} [{}]: {}",
                    parkId,
                    month,
                    exception.getDiagnosticCode(),
                    exception.getMessage()
            );
        }

        try {
            SixFlagsCalendarResponse response =
                    fetch(
                            fallbackClient,
                            fallbackBaseUrl,
                            month
                    );

            LOGGER.warn(
                    "Six Flags calendar loaded from fallback endpoint "
                            + "for park {} month {} after primary failure [{}]",
                    parkId,
                    month,
                    primaryFailure.getDiagnosticCode()
            );

            return response;

        } catch (SixFlagsCalendarException fallbackFailure) {

            LOGGER.error(
                    "Both Six Flags calendar endpoints failed for park {} "
                            + "month {}. Primary=[{}], fallback=[{}]",
                    parkId,
                    month,
                    primaryFailure.getDiagnosticCode(),
                    fallbackFailure.getDiagnosticCode()
            );

            String diagnosticCode =
                    chooseDiagnosticCode(
                            primaryFailure,
                            fallbackFailure
                    );

            throw new SixFlagsCalendarException(
                    diagnosticCode,
                    "Both Six Flags calendar endpoints failed. "
                            + "Primary: "
                            + primaryFailure.getMessage()
                            + "; fallback: "
                            + fallbackFailure.getMessage(),
                    fallbackFailure
            );
        }
    }

    public int getParkId() {
        return parkId;
    }

    private SixFlagsCalendarResponse fetch(
            RestClient client,
            String baseUrl,
            String month) {

        try {
            SixFlagsCalendarResponse response =
                    client.get()
                            .uri(
                                    "/operating-hours/park/{parkId}?date={month}",
                                    parkId,
                                    month
                            )
                            .retrieve()
                            .body(SixFlagsCalendarResponse.class);

            if (response == null) {
                throw new SixFlagsCalendarException(
                        "EMPTY_RESPONSE",
                        "Calendar endpoint returned an empty response from "
                                + baseUrl,
                        null
                );
            }

            return response;

        } catch (SixFlagsCalendarException exception) {
            throw exception;

        } catch (HttpStatusCodeException exception) {

            throw new SixFlagsCalendarException(
                    "HTTP_ERROR_"
                            + exception.getStatusCode().value(),
                    "Calendar endpoint returned HTTP "
                            + exception.getStatusCode().value()
                            + " from "
                            + baseUrl,
                    exception
            );

        } catch (ResourceAccessException exception) {

            String diagnosticCode =
                    causedByTimeout(exception)
                            ? "TIMEOUT"
                            : "NETWORK_ERROR";

            throw new SixFlagsCalendarException(
                    diagnosticCode,
                    "Calendar endpoint could not be reached at "
                            + baseUrl,
                    exception
            );

        } catch (RestClientException exception) {

            throw new SixFlagsCalendarException(
                    "UNEXPECTED_RESPONSE",
                    "Calendar response could not be read from "
                            + baseUrl,
                    exception
            );
        }
    }

    private String chooseDiagnosticCode(
            SixFlagsCalendarException primaryFailure,
            SixFlagsCalendarException fallbackFailure) {

        if ("TIMEOUT".equals(
                fallbackFailure.getDiagnosticCode()
        )) {
            return "TIMEOUT";
        }

        if ("TIMEOUT".equals(
                primaryFailure.getDiagnosticCode()
        )) {
            return "TIMEOUT";
        }

        return fallbackFailure.getDiagnosticCode();
    }

    private boolean causedByTimeout(Throwable throwable) {

        Throwable current = throwable;

        while (current != null) {

            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private JdkClientHttpRequestFactory createRequestFactory() {

        HttpClient httpClient = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(
                Duration.ofSeconds(5)
        );

        return requestFactory;
    }
}
