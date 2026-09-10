package technology.renatus.routepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RoutePulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                RoutePulseApplication.class,
                args
        );
    }
}
