package technology.renatus.routepulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import jakarta.persistence.EntityManager;
import technology.renatus.routepulse.dataTransferObject.InvitationAcceptanceForm;
import technology.renatus.routepulse.dataTransferObject.RegistrationForm;
import technology.renatus.routepulse.model.AssignmentSource;
import technology.renatus.routepulse.model.Driver;
import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.model.OrganizationInvitation;
import technology.renatus.routepulse.model.OrganizationMembership;
import technology.renatus.routepulse.model.OrganizationRole;
import technology.renatus.routepulse.model.OrganizationStatus;
import technology.renatus.routepulse.model.ScheduleImport;
import technology.renatus.routepulse.model.ShiftRecord;
import technology.renatus.routepulse.model.ShuttlePeriod;
import technology.renatus.routepulse.model.UploadedShift;
import technology.renatus.routepulse.model.UserAccount;
import technology.renatus.routepulse.provider.CsvScheduleProvider;
import technology.renatus.routepulse.repository.DriverAssignmentRepository;
import technology.renatus.routepulse.repository.DriverRepository;
import technology.renatus.routepulse.repository.OrganizationMembershipRepository;
import technology.renatus.routepulse.repository.OrganizationRepository;
import technology.renatus.routepulse.repository.ScheduleImportRepository;
import technology.renatus.routepulse.repository.UploadedShiftRepository;
import technology.renatus.routepulse.repository.UserAccountRepository;
import technology.renatus.routepulse.security.DatabaseUserDetailsService;
import technology.renatus.routepulse.service.CurrentOrganizationService;
import technology.renatus.routepulse.service.DriverAssignmentService;
import technology.renatus.routepulse.service.DriverService;
import technology.renatus.routepulse.service.EmailVerificationService;
import technology.renatus.routepulse.service.InvitationEmailSender;
import technology.renatus.routepulse.service.OrganizationInvitationService;
import technology.renatus.routepulse.service.PasswordResetEmailSender;
import technology.renatus.routepulse.service.PasswordResetService;
import technology.renatus.routepulse.service.RegistrationService;
import technology.renatus.routepulse.service.VerificationEmailSender;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.password=test",
        "app.base-url=http://localhost:8080",
        "app.operations-zone=UTC"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(RoutePulseIntegrationTests.RecordingEmailConfiguration.class)
class RoutePulseIntegrationTests {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("routepulse_test")
                    .withUsername("routepulse_test")
                    .withPassword("routepulse_test");

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private DriverAssignmentRepository assignmentRepository;

    @Autowired
    private ScheduleImportRepository scheduleImportRepository;

    @Autowired
    private UploadedShiftRepository uploadedShiftRepository;

    @Autowired
    private CurrentOrganizationService currentOrganizationService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private DriverAssignmentService assignmentService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private EmailVerificationService verificationService;

    @Autowired
    private OrganizationInvitationService invitationService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private DatabaseUserDetailsService userDetailsService;

    @Autowired
    private CsvScheduleProvider csvScheduleProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RecordingVerificationEmailSender verificationEmailSender;

    @Autowired
    private RecordingInvitationEmailSender invitationEmailSender;

    @Autowired
    private RecordingPasswordResetEmailSender passwordResetEmailSender;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void clearRecordedEmails() {
        verificationEmailSender.clear();
        invitationEmailSender.clear();
        passwordResetEmailSender.clear();
    }

    @Test
    void allMigrationsRunAgainstABlankMySqlDatabase() {
        assertNotNull(flyway.info().current());

        assertEquals(
                "26",
                flyway.info().current().getVersion().getVersion()
        );

        Integer successfulMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = 1
                """,
                Integer.class
        );

        Integer failedMigrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE success = 0
                """,
                Integer.class
        );

        assertEquals(26, successfulMigrations);
        assertEquals(0, failedMigrations);
    }

    @Test
    void organizationACannotAccessOrganizationBsRecords() {
        Organization organizationA = createOrganization(
                "Organization A",
                "organization-a"
        );

        Organization organizationB = createOrganization(
                "Organization B",
                "organization-b"
        );

        UserAccount ownerA = createUser(
                "owner-a@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organizationA,
                        ownerA,
                        OrganizationRole.OWNER
                )
        );

        Driver driverA = driverRepository.save(
                new Driver(
                        organizationA,
                        "100",
                        "Driver A",
                        true
                )
        );

        Driver driverB = driverRepository.save(
                new Driver(
                        organizationB,
                        "100",
                        "Driver B",
                        true
                )
        );

        Authentication authenticationA = authenticationFor(
                ownerA,
                OrganizationRole.OWNER
        );

        Long currentOrganizationId =
                currentOrganizationService
                        .requireOrganizationId(authenticationA);

        assertEquals(
                organizationA.getId(),
                currentOrganizationId
        );

        assertEquals(
                List.of(driverA.getId()),
                driverService
                        .getAllDrivers(currentOrganizationId)
                        .stream()
                        .map(Driver::getId)
                        .toList()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> driverService.updateActiveStatus(
                        currentOrganizationId,
                        driverB.getId(),
                        false
                )
        );

        DriverAssignment organizationBAssignment =
                assignmentService.createManualAssignment(
                        organizationB.getId(),
                        LocalDate.of(2026, 9, 20),
                        ShuttlePeriod.MORNING,
                        driverB.getId(),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "Organization B coverage"
                );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.updateScheduledTimes(
                        currentOrganizationId,
                        organizationBAssignment.getId(),
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0)
                )
        );
    }

    @Test
    void registrationCreatesAnOwnerAndEmailVerificationEnablesLogin() {
        RegistrationForm form = new RegistrationForm();

        form.setOrganizationLegalName(
                "Acme Operations LLC"
        );

        form.setOrganizationDisplayName(
                "Acme Operations"
        );

        form.setFirstName("Victor");
        form.setLastName("Pena");
        form.setEmail("  OWNER@EXAMPLE.COM  ");
        form.setPassword("a-strong-password");
        form.setConfirmPassword("a-strong-password");

        registrationService.registerOrganizationOwner(form);

        UserAccount owner = userAccountRepository
                .findByEmailIgnoreCase("owner@example.com")
                .orElseThrow();

        assertFalse(owner.isEmailVerified());
        assertTrue(owner.isEnabled());

        assertTrue(
                passwordEncoder.matches(
                        "a-strong-password",
                        owner.getPasswordHash()
                )
        );

        OrganizationMembership membership =
                membershipRepository
                        .findAllByUser_Id(owner.getId())
                        .get(0);

        assertEquals(
                OrganizationRole.OWNER,
                membership.getRole()
        );

        assertEquals(
                "acme-operations",
                membership.getOrganization().getSlug()
        );

        UserDetails beforeVerification =
                userDetailsService.loadUserByUsername(
                        "owner@example.com"
                );

        assertFalse(beforeVerification.isEnabled());

        String originalToken = queryParameter(
                verificationEmailSender.getLastUrl(),
                "token"
        );

        verificationEmailSender.clear();

        verificationService.resendVerificationEmail(
                "  OWNER@EXAMPLE.COM  "
        );

        String replacementToken = queryParameter(
                verificationEmailSender.getLastUrl(),
                "token"
        );

        assertEquals(
                EmailVerificationService
                        .VerificationResult
                        .ALREADY_USED,
                verificationService.verifyEmail(originalToken)
        );

        assertEquals(
                EmailVerificationService
                        .VerificationResult
                        .VERIFIED,
                verificationService.verifyEmail(replacementToken)
        );

        assertTrue(owner.isEmailVerified());

        UserDetails afterVerification =
                userDetailsService.loadUserByUsername(
                        "owner@example.com"
                );

        assertTrue(afterVerification.isEnabled());

        assertEquals(
                EmailVerificationService
                        .VerificationResult
                        .ALREADY_USED,
                verificationService.verifyEmail(replacementToken)
        );
    }

    @Test
    void ownerCanInviteAndCreateAManagerAccount() {
        Organization organization = createOrganization(
                "Invitation Organization",
                "invitation-organization"
        );

        UserAccount owner = createUser(
                "owner@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organization,
                        owner,
                        OrganizationRole.OWNER
                )
        );

        Authentication ownerAuthentication =
                authenticationFor(
                        owner,
                        OrganizationRole.OWNER
                );

        invitationService.createAndSendManagerInvitation(
                ownerAuthentication,
                "  MANAGER@EXAMPLE.COM  "
        );

        String rawToken = queryParameter(
                invitationEmailSender.getLastUrl(),
                "token"
        );

        OrganizationInvitation invitation =
                invitationService
                        .getInvitationForAcceptance(rawToken);

        assertEquals(
                "manager@example.com",
                invitation.getInvitedEmail()
        );

        assertEquals(
                OrganizationRole.ADMIN,
                invitation.getRole()
        );

        assertFalse(invitation.isAccepted());

        InvitationAcceptanceForm form =
                new InvitationAcceptanceForm();

        form.setToken(rawToken);
        form.setFirstName("New");
        form.setLastName("Manager");
        form.setPassword("manager-password");
        form.setConfirmPassword("manager-password");

        invitationService.acceptInvitation(form);

        UserAccount manager = userAccountRepository
                .findByEmailIgnoreCase("manager@example.com")
                .orElseThrow();

        OrganizationMembership managerMembership =
                membershipRepository
                        .findByOrganization_IdAndUser_Id(
                                organization.getId(),
                                manager.getId()
                        )
                        .orElseThrow();

        assertTrue(manager.isEnabled());
        assertTrue(manager.isEmailVerified());

        assertTrue(
                passwordEncoder.matches(
                        "manager-password",
                        manager.getPasswordHash()
                )
        );

        assertEquals(
                OrganizationRole.ADMIN,
                managerMembership.getRole()
        );

        assertTrue(invitation.isAccepted());
        assertNotNull(invitation.getAcceptedAt());

        assertThrows(
                IllegalArgumentException.class,
                () -> invitationService
                        .getInvitationForAcceptance(rawToken)
        );
    }

    @Test
    void csvUploadKeepsHistoryButSelectsOnlyTheNewestImportForADate() {
        Organization organization = createOrganization(
                "CSV Organization",
                "csv-organization"
        );

        UserAccount uploader = createUser(
                "uploader@example.com",
                true
        );

        LocalDate shiftDate =
                LocalDate.of(2026, 9, 15);

        ScheduleImport firstImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "first.csv",
                                "100,Old Driver,9/15/2026 8:00 AM,9/15/2026 4:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver"
                        )
                );

        entityManager.flush();

        LocalDateTime oldImportTime =
                LocalDateTime.of(
                        2026,
                        1,
                        1,
                        0,
                        0
                );

        jdbcTemplate.update(
                """
                UPDATE schedule_imports
                SET uploaded_at = ?
                WHERE id = ?
                """,
                Timestamp.valueOf(oldImportTime),
                firstImport.getId()
        );

        jdbcTemplate.update(
                """
                UPDATE uploaded_shifts
                SET imported_at = ?
                WHERE schedule_import_id = ?
                """,
                Timestamp.valueOf(oldImportTime),
                firstImport.getId()
        );

        entityManager.clear();

        ScheduleImport newestImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "newest.csv",
                                "200,Current Driver,9/15/2026 9:00 AM,9/15/2026 5:00 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver"
                        )
                );

        List<ShiftRecord> currentSchedule =
                csvScheduleProvider.getSchedulesForDate(
                        organization.getId(),
                        shiftDate
                );

        assertEquals(
                2,
                scheduleImportRepository.count()
        );

        assertEquals(
                2,
                uploadedShiftRepository.count()
        );

        assertEquals(1, currentSchedule.size());

        assertEquals(
                "200",
                currentSchedule.get(0).getEmployeeId()
        );

        assertEquals(
                "Current Driver",
                currentSchedule.get(0).getEmployeeName()
        );

        assertEquals(
                "South Gate",
                currentSchedule.get(0).getPickupLocation()
        );

        assertEquals(
                1,
                newestImport.getRowCount()
        );

        assertEquals(
                "newest.csv",
                newestImport.getOriginalFilename()
        );

        assertEquals(
                shiftDate,
                csvScheduleProvider
                        .getFirstDateOfLatestImport(
                                organization.getId()
                        )
        );

        assertEquals(
                List.of(
                        "newest.csv",
                        "first.csv"
                ),
                csvScheduleProvider
                        .getRecentImports(
                                organization.getId()
                        )
                        .stream()
                        .map(ScheduleImport::getOriginalFilename)
                        .toList()
        );
    }

    @Test
    void csvUploadRejectsARowWithOnlyOneShiftTime() {
        Organization organization = createOrganization(
                "Malformed CSV Organization",
                "malformed-csv-organization"
        );

        UserAccount uploader = createUser(
                "malformed-csv@example.com",
                true
        );

        assertThrows(
                IllegalStateException.class,
                () -> csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "malformed.csv",
                                "100,Driver,9/15/2026 8:00 AM,,Park/Ops/Transportation/Shuttle/101 North Gate,Driver"
                        )
                )
        );

        assertEquals(
                0,
                scheduleImportRepository.count()
        );

        assertEquals(
                0,
                uploadedShiftRepository.count()
        );
    }

    @Test
    void automaticManualAndOverrideAssignmentsPreserveTheirAuditMeaning() {
        Organization organization = createOrganization(
                "Assignment Organization",
                "assignment-organization"
        );

        UserAccount uploader = createUser(
                "assignments@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organization,
                        uploader,
                        OrganizationRole.ADMIN
                )
        );

        Driver alice = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "Alice",
                        true
                )
        );

        Driver bob = driverRepository.save(
                new Driver(
                        organization,
                        "200",
                        "Bob",
                        true
                )
        );

        Driver charlie = driverRepository.save(
                new Driver(
                        organization,
                        "300",
                        "Charlie",
                        true
                )
        );

        LocalDate assignmentDate =
                LocalDate.of(2026, 9, 16);

        ScheduleImport sourceImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "drivers.csv",
                                "100,Alice,9/16/2026 8:00 AM,9/16/2026 4:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver",
                                "200,Bob,9/16/2026 8:30 AM,9/16/2026 4:30 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver"
                        )
                );

        List<DriverAssignment> automaticAssignments =
                assignmentService.getOrCreateAssignments(
                        organization.getId(),
                        assignmentDate
                );

        assertEquals(
                2,
                automaticAssignments.size()
        );

        DriverAssignment morning = assignmentFor(
                automaticAssignments,
                ShuttlePeriod.MORNING
        );

        assertEquals(
                AssignmentSource.AUTO,
                morning.getAssignmentSource()
        );

        assertEquals(
                alice.getId(),
                morning.getDriver().getId()
        );

        assertEquals(
                alice.getId(),
                morning.getOriginallySuggestedDriver().getId()
        );

        assertEquals(
                sourceImport.getId(),
                morning.getSourceScheduleImport().getId()
        );

        DriverAssignment overridden =
                assignmentService.changeAssignedDriver(
                        organization.getId(),
                        morning.getId(),
                        charlie.getId(),
                        "Manager coverage change",
                        uploader.getEmail()
                );

        assertEquals(
                AssignmentSource.OVERRIDE,
                overridden.getAssignmentSource()
        );

        assertEquals(
                charlie.getId(),
                overridden.getFinalDriver().getId()
        );

        assertEquals(
                alice.getId(),
                overridden
                        .getOriginallySuggestedDriver()
                        .getId()
        );

        assertEquals(
                "Manager coverage change",
                overridden.getChangeReason()
        );

        assertNotNull(overridden.getChangedAt());

        assertEquals(
                uploader.getId(),
                overridden.getChangedBy().getId()
        );

        DriverAssignment manual =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        assignmentDate.plusDays(1),
                        ShuttlePeriod.AFTERNOON,
                        bob.getId(),
                        LocalTime.of(15, 0),
                        LocalTime.of(17, 0),
                        "No uploaded schedule"
                );

        assertEquals(
                AssignmentSource.MANUAL,
                manual.getAssignmentSource()
        );

        assertEquals(
                bob.getId(),
                manual.getFinalDriver().getId()
        );

        assertNull(
                manual.getOriginallySuggestedDriver()
        );

        assertEquals(
                120,
                manual.getScheduledMinutes()
        );

        assertEquals(
                "No uploaded schedule",
                manual.getChangeReason()
        );

        ScheduleImport correctedImport =
                csvScheduleProvider.loadSchedule(
                        organization.getId(),
                        uploader.getEmail(),
                        csvFile(
                                "corrected-drivers.csv",
                                "100,Alice,9/16/2026 9:00 AM,9/16/2026 5:00 PM,Park/Ops/Transportation/Shuttle/101 North Gate,Driver",
                                "200,Bob,9/16/2026 9:30 AM,9/16/2026 5:30 PM,Park/Ops/Transportation/Shuttle/202 South Gate,Driver"
                        )
                );

        assertTrue(
                morning.requiresReviewAgainst(
                        correctedImport.getId()
                )
        );

        assertFalse(
                manual.requiresReviewAgainst(
                        correctedImport.getId()
                )
        );

        assignmentService
                .confirmAssignmentsAgainstLatestImport(
                        organization.getId(),
                        assignmentDate,
                        uploader.getEmail()
                );

        assertFalse(
                morning.requiresReviewAgainst(
                        correctedImport.getId()
                )
        );

        assertEquals(
                uploader.getId(),
                morning.getScheduleReviewedBy().getId()
        );
    }

    @Test
    void startAndEndDrivingEnforceTheLifecycleRules() {
        Organization organization = createOrganization(
                "Driving Organization",
                "driving-organization"
        );

        UserAccount operator = createUser(
                "driving-operator@example.com",
                true
        );

        membershipRepository.save(
                new OrganizationMembership(
                        organization,
                        operator,
                        OrganizationRole.ADMIN
                )
        );

        Driver firstDriver = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "First Driver",
                        true
                )
        );

        Driver secondDriver = driverRepository.save(
                new Driver(
                        organization,
                        "200",
                        "Second Driver",
                        true
                )
        );

        LocalDate today =
                assignmentService.getCurrentOperationsDate();

        DriverAssignment assignment =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        today,
                        ShuttlePeriod.MORNING,
                        firstDriver.getId(),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "Daily coverage"
                );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.endDriving(
                        organization.getId(),
                        assignment.getId(),
                        operator.getEmail()
                )
        );

        assignmentService.startDriving(
                organization.getId(),
                assignment.getId(),
                operator.getEmail()
        );

        assertTrue(assignment.isDriving());
        assertNotNull(assignment.getActualStartedAt());

        assertEquals(
                operator.getId(),
                assignment.getActualStartedBy().getId()
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.startDriving(
                        organization.getId(),
                        assignment.getId(),
                        operator.getEmail()
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.updateScheduledTimes(
                        organization.getId(),
                        assignment.getId(),
                        LocalTime.of(8, 30),
                        LocalTime.of(10, 30)
                )
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.changeAssignedDriver(
                        organization.getId(),
                        assignment.getId(),
                        secondDriver.getId(),
                        "Too late",
                        operator.getEmail()
                )
        );

        assignmentService.endDriving(
                organization.getId(),
                assignment.getId(),
                operator.getEmail()
        );

        assertTrue(assignment.isDrivingCompleted());
        assertNotNull(assignment.getActualEndedAt());

        assertEquals(
                operator.getId(),
                assignment.getActualEndedBy().getId()
        );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.endDriving(
                        organization.getId(),
                        assignment.getId(),
                        operator.getEmail()
                )
        );

        DriverAssignment missingSchedule =
                assignmentRepository.save(
                        DriverAssignment.automatic(
                                today,
                                ShuttlePeriod.AFTERNOON,
                                secondDriver
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () -> assignmentService.startDriving(
                        organization.getId(),
                        missingSchedule.getId(),
                        operator.getEmail()
                )
        );
    }

    @Test
    void managerCanAssignOneDriverToBothShuttlePeriods() {
        Organization organization = createOrganization(
                "Emergency Coverage Organization",
                "emergency-coverage-organization"
        );

        Driver driver = driverRepository.save(
                new Driver(
                        organization,
                        "100",
                        "Only Available Driver",
                        true
                )
        );

        LocalDate assignmentDate =
                LocalDate.of(2026, 9, 18);

        DriverAssignment morning =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        assignmentDate,
                        ShuttlePeriod.MORNING,
                        driver.getId(),
                        LocalTime.of(8, 0),
                        LocalTime.of(10, 0),
                        "Emergency coverage"
                );

        DriverAssignment afternoon =
                assignmentService.createManualAssignment(
                        organization.getId(),
                        assignmentDate,
                        ShuttlePeriod.AFTERNOON,
                        driver.getId(),
                        LocalTime.of(15, 0),
                        LocalTime.of(17, 0),
                        "Emergency coverage"
                );

        assertEquals(
                morning.getDriver().getId(),
                afternoon.getDriver().getId()
        );
    }

@Test
void httpSecurityProtectsOrganizationRoutesAndPostRequests()
        throws Exception {

    Organization organization = createOrganization(
            "HTTP Security Organization",
            "http-security-organization"
    );

    UserAccount owner = createUser(
            "http-owner@example.com",
            true
    );

    UserAccount admin = createUser(
            "http-admin@example.com",
            true
    );

    membershipRepository.save(
            new OrganizationMembership(
                    organization,
                    owner,
                    OrganizationRole.OWNER
            )
    );

    membershipRepository.save(
            new OrganizationMembership(
                    organization,
                    admin,
                    OrganizationRole.ADMIN
            )
    );

    uploadedShiftRepository.save(
            new UploadedShift(
                    organization,
                    null,
                    "100",
                    "Protected Driver",
                    "Transportation",
                    "Shuttle Driver",
                    LocalDate.of(2026, 9, 4),
                    LocalTime.of(8, 0),
                    LocalTime.of(17, 0),
                    "Employee entrance",
                    LocalDateTime.now()
            )
    );

    /*
     * The RoutePulse landing page is intentionally public.
     * Anonymous visitors should be able to view the homepage
     * and submit a demo request.
     */
    mockMvc.perform(
            get("/")
    ).andExpect(
            status().isOk()
    );

    /*
     * Authenticated POST without CSRF must still be rejected.
     */
    mockMvc.perform(
            post("/drivers")
                    .with(
                            user(owner.getEmail())
                                    .roles("OWNER")
                    )
                    .param("employeeId", "100")
                    .param("active", "true")
    ).andExpect(
            status().isForbidden()
    );

    /*
     * Owner with a valid CSRF token is allowed to
     * register the driver.
     */
    mockMvc.perform(
            post("/drivers")
                    .with(
                            user(owner.getEmail())
                                    .roles("OWNER")
                    )
                    .with(csrf())
                    .param("employeeId", "100")
                    .param("active", "true")
    ).andExpect(
            status().is3xxRedirection()
    );

    assertTrue(
            driverRepository
                    .existsByOrganization_IdAndEmployeeId(
                            organization.getId(),
                            "100"
                    )
    );

    Driver registeredDriver =
            driverRepository
                    .findAllByOrganization_IdOrderByNameAsc(
                            organization.getId()
                    )
                    .get(0);

    assertEquals(
            "Protected Driver",
            registeredDriver.getName()
    );

    /*
     * ADMIN is still not allowed to use owner-only
     * organization invitation management.
     */
    mockMvc.perform(
            get("/organization/invitations")
                    .with(
                            user(admin.getEmail())
                                    .roles("ADMIN")
                    )
    ).andExpect(
            status().isForbidden()
    );

    /*
     * Password recovery and verification recovery
     * pages remain public.
     */
    mockMvc.perform(
            get("/forgot-password")
    ).andExpect(
            status().isOk()
    );

    mockMvc.perform(
            get("/resend-verification")
    ).andExpect(
            status().isOk()
    );
}
    @Test
    void passwordResetChangesThePasswordAndInvalidatesTheToken() {
        UserAccount user = createUser(
                "reset@example.com",
                true
        );

        passwordResetService.requestPasswordReset(
                "  RESET@EXAMPLE.COM  "
        );

        String rawToken = queryParameter(
                passwordResetEmailSender.getLastUrl(),
                "token"
        );

        assertEquals(
                PasswordResetService.TokenStatus.VALID,
                passwordResetService.inspectToken(rawToken)
        );

        assertEquals(
                PasswordResetService.ResetResult.RESET,
                passwordResetService.resetPassword(
                        rawToken,
                        "a-new-strong-password",
                        "a-new-strong-password"
                )
        );

        assertTrue(
                passwordEncoder.matches(
                        "a-new-strong-password",
                        user.getPasswordHash()
                )
        );

        assertEquals(
                PasswordResetService.TokenStatus.USED,
                passwordResetService.inspectToken(rawToken)
        );

        assertEquals(
                PasswordResetService.ResetResult.USED,
                passwordResetService.resetPassword(
                        rawToken,
                        "another-strong-password",
                        "another-strong-password"
                )
        );

        passwordResetEmailSender.clear();

        passwordResetService.requestPasswordReset(
                "missing@example.com"
        );

        assertNull(
                passwordResetEmailSender.getLastUrl()
        );
    }

    private Organization createOrganization(
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

    private UserAccount createUser(
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

    private Authentication authenticationFor(
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

    private MockMultipartFile csvFile(
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

    private DriverAssignment assignmentFor(
            List<DriverAssignment> assignments,
            ShuttlePeriod period) {

        return assignments.stream()
                .filter(assignment ->
                        assignment.getPeriod() == period
                )
                .findFirst()
                .orElseThrow();
    }

    private String queryParameter(
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
    static class RecordingEmailConfiguration {

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

    static class RecordingVerificationEmailSender
            implements VerificationEmailSender {

        private String lastUrl;

        @Override
        public void sendVerificationEmail(
                UserAccount user,
                String verificationUrl) {

            lastUrl = verificationUrl;
        }

        String getLastUrl() {
            return lastUrl;
        }

        void clear() {
            lastUrl = null;
        }
    }

    static class RecordingInvitationEmailSender
            implements InvitationEmailSender {

        private String lastUrl;

        @Override
        public void sendManagerInvitation(
                OrganizationInvitation invitation,
                String invitationUrl) {

            lastUrl = invitationUrl;
        }

        String getLastUrl() {
            return lastUrl;
        }

        void clear() {
            lastUrl = null;
        }
    }

    static class RecordingPasswordResetEmailSender
            implements PasswordResetEmailSender {

        private String lastUrl;

        @Override
        public void sendPasswordResetEmail(
                UserAccount user,
                String resetUrl) {

            lastUrl = resetUrl;
        }

        String getLastUrl() {
            return lastUrl;
        }

        void clear() {
            lastUrl = null;
        }
    }
}