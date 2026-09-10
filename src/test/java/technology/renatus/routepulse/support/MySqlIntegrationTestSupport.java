package technology.renatus.routepulse.support;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.mysql.MySQLContainer;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.assignments.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.assignments.service.DriverAssignmentService;
import technology.renatus.routepulse.auth.email.PasswordResetEmailSender;
import technology.renatus.routepulse.auth.email.VerificationEmailSender;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.auth.repository.UserAccountRepository;
import technology.renatus.routepulse.auth.service.EmailVerificationService;
import technology.renatus.routepulse.auth.service.PasswordResetService;
import technology.renatus.routepulse.auth.service.RegistrationService;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driver.service.DriverService;
import technology.renatus.routepulse.organization.domain.OrganizationRole;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.email.InvitationEmailSender;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.model.OrganizationInvitation;
import technology.renatus.routepulse.organization.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.organization.service.InvitationAcceptanceService;
import technology.renatus.routepulse.organization.service.OrganizationInvitationService;
import technology.renatus.routepulse.scheduling.importing.CsvScheduleProvider;
import technology.renatus.routepulse.scheduling.repository.ScheduleImportRepository;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;
import technology.renatus.routepulse.shared.security.DatabaseUserDetailsService;

/** Shared fixture; the MySQL container lives for the test JVM and each test rolls back. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.base-url=http://localhost:8080",
        "app.operations-zone=UTC",
        "app.retention.enabled=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
@Import(MySqlIntegrationTestSupport.RecordingEmailConfiguration.class)
public abstract class MySqlIntegrationTestSupport {
    private static final class Database {
        private static final MySQLContainer MYSQL = startDatabase();

        private static MySQLContainer startDatabase() {
            MySQLContainer database = new MySQLContainer("mysql:8.4")
                    .withDatabaseName("routepulse_test")
                    .withUsername("routepulse_test")
                    .withPassword("routepulse_test");
            database.start();
            return database;
        }
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Database.MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", Database.MYSQL::getUsername);
        registry.add("spring.datasource.password", Database.MYSQL::getPassword);
    }

    @Autowired
    protected OrganizationRepository organizationRepository;

    @Autowired
    protected UserAccountRepository userAccountRepository;

    @Autowired
    protected OrganizationMembershipRepository membershipRepository;

    @Autowired
    protected DriverRepository driverRepository;

    @Autowired
    protected DriverAssignmentRepository assignmentRepository;

    @Autowired
    protected ScheduleImportRepository scheduleImportRepository;

    @Autowired
    protected UploadedShiftRepository uploadedShiftRepository;

    @Autowired
    protected CurrentOrganizationService currentOrganizationService;

    @Autowired
    protected DriverService driverService;

    @Autowired
    protected DriverAssignmentService assignmentService;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected EmailVerificationService verificationService;

    @Autowired
    protected OrganizationInvitationService invitationService;

    @Autowired
    protected PasswordResetService passwordResetService;

    @Autowired
    protected DatabaseUserDetailsService userDetailsService;

    @Autowired
    protected CsvScheduleProvider csvScheduleProvider;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected RecordingVerificationEmailSender verificationEmailSender;

    @Autowired
    protected RecordingInvitationEmailSender invitationEmailSender;

    @Autowired
    protected RecordingPasswordResetEmailSender passwordResetEmailSender;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected Flyway flyway;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected InvitationAcceptanceService invitationAcceptanceService;

    @BeforeEach
    void clearRecordedEmails() {
        verificationEmailSender.clear();
        invitationEmailSender.clear();
        passwordResetEmailSender.clear();
    }

    protected Organization createOrganization(
            String displayName,
            String slug) {

        return organizationRepository.save(
                new Organization(
                        displayName + " LLC",
                        displayName,
                        slug,
                        OrganizationStatus.ACTIVE,
                        null,
                        null
                )
        );
    }

    protected UserAccount createUser(
            String email,
            boolean emailVerified) {

        return userAccountRepository.save(
                new UserAccount(
                        email,
                        passwordEncoder.encode(
                                "test-password"
                        ),
                        "Test",
                        "User",
                        emailVerified,
                        true
                )
        );
    }

    protected Authentication authenticationFor(
            UserAccount user,
            OrganizationRole role) {

        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                "not-used",
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + role.name()
                        )
                )
        );
    }

    protected MockMultipartFile csvFile(
            String filename,
            String... rows) {

        String csv =
                "Report generated by Dayforce\n"
                + "Employee ID,Employee Name,Shift Start,Shift End,Location Path,Primary Job\n"
                + String.join("\n", rows)
                + "\n";

        return new MockMultipartFile(
                "file",
                filename,
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }

    protected DriverAssignment assignmentFor(
            List<DriverAssignment> assignments,
            ShuttlePeriod period) {

        return assignments.stream()
                .filter(assignment ->
                        assignment.getPeriod() == period
                )
                .findFirst()
                .orElseThrow();
    }

    protected String queryParameter(
            String url,
            String parameterName) {

        assertNotNull(url);

        String value = UriComponentsBuilder
                .fromUriString(url)
                .build()
                .getQueryParams()
                .getFirst(parameterName);

        assertNotNull(value);

        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class RecordingEmailConfiguration {

        @Bean
        @Primary
        RecordingVerificationEmailSender
                recordingVerificationEmailSender() {

            return new RecordingVerificationEmailSender();
        }

        @Bean
        @Primary
        RecordingInvitationEmailSender
                recordingInvitationEmailSender() {

            return new RecordingInvitationEmailSender();
        }

        @Bean
        @Primary
        RecordingPasswordResetEmailSender
                recordingPasswordResetEmailSender() {

            return new RecordingPasswordResetEmailSender();
        }
    }

    public static class RecordingVerificationEmailSender
            implements VerificationEmailSender {

        private String lastUrl;

        @Override
        public void sendVerificationEmail(
                UserAccount user,
                String verificationUrl) {

            lastUrl = verificationUrl;
        }

        public String getLastUrl() {
            return lastUrl;
        }

        public void clear() {
            lastUrl = null;
        }
    }

    public static class RecordingInvitationEmailSender
            implements InvitationEmailSender {

        private String lastUrl;

        @Override
        public void sendManagerInvitation(
                OrganizationInvitation invitation,
                String invitationUrl) {

            lastUrl = invitationUrl;
        }

        public String getLastUrl() {
            return lastUrl;
        }

        public void clear() {
            lastUrl = null;
        }
    }

    public static class RecordingPasswordResetEmailSender
            implements PasswordResetEmailSender {

        private String lastUrl;

        @Override
        public void sendPasswordResetEmail(
                UserAccount user,
                String resetUrl) {

            lastUrl = resetUrl;
        }

        public String getLastUrl() {
            return lastUrl;
        }

        public void clear() {
            lastUrl = null;
        }
    }
}
