package technology.renatus.routepulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication
public class ShuttleflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                ShuttleflowApplication.class,
                args
        );
    }
}