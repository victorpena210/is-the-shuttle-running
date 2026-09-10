package technology.renatus.routepulse.shared.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Emits one startup summary so a deployment log immediately shows whether the
 * production error-reporting layer is actually configured. Missing Sentry does
 * not stop the app: monitoring should never become an availability dependency.
 */
@Component
public class ProductionObservabilityReporter implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(ProductionObservabilityReporter.class);

    private final Environment environment;

    public ProductionObservabilityReporter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = environment.matchesProfiles("prod");
        String dsn = environment.getProperty("sentry.dsn");
        boolean sentryConfigured = StringUtils.hasText(dsn);

        if (production && !sentryConfigured) {
            log.warn(
                    "Production is running without Sentry error reporting. "
                    + "Set SENTRY_DSN to enable exception alerts."
            );
        } else if (production) {
            log.info(
                    "Production monitoring enabled: Sentry configured; "
                    + "readiness endpoint=/actuator/health/readiness"
            );
        }
    }
}
