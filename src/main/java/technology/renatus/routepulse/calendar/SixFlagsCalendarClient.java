package technology.renatus.routepulse.calendar;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SixFlagsCalendarClient {
	
	private static final DateTimeFormatter MONTH_FORMAT = 
			DateTimeFormatter.ofPattern("yyyyMM");
	
	private final RestClient restClient;
	private final int parkId;
	
    public SixFlagsCalendarClient(
            RestClient.Builder restClientBuilder,
            @Value("${sixflags.calendar.base-url}") String baseUrl,
            @Value("${sixflags.calendar.park-id}") int parkId) {

        this.restClient = restClientBuilder
                .requestFactory(createRequestFactory())
                .baseUrl(baseUrl)
                .build();

        this.parkId = parkId;
        
        
    }

    
    public SixFlagsCalendarResponse getCalendarForMonth(
            LocalDate date) {

        String month = date.format(MONTH_FORMAT);

        return restClient.get()
                .uri(
                    "/operating-hours/park/{parkId}?date={month}",
                    parkId,
                    month
                )
                .retrieve()
                .body(SixFlagsCalendarResponse.class);
    }

    private JdkClientHttpRequestFactory
            createRequestFactory() {

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
