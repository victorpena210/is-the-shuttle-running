package technology.renatus.routepulse.driving.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import technology.renatus.routepulse.assignments.domain.ShuttlePeriod;
import technology.renatus.routepulse.assignments.model.DriverAssignment;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.model.PassengerRideConfirmation;
import technology.renatus.routepulse.driving.repository.PassengerRideConfirmationRepository;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationConfidence;
import technology.renatus.routepulse.driving.verification.model.ServiceVerificationPeriod;
import technology.renatus.routepulse.driving.verification.service.ServiceVerificationService;
import technology.renatus.routepulse.scheduling.domain.PickupPriority;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandAnalysis;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandPeriod;
import technology.renatus.routepulse.scheduling.domain.TransportationDemandWindow;

class ServiceVerificationServiceTests {

    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 10);

    @Test
    void marksWindowVerifiedWhenDrivingAndPassengerSignalsAgree() {
        PassengerRideConfirmationRepository repository = mock(PassengerRideConfirmationRepository.class);
        DriverAssignment assignment = assignment(42L);

        DrivingSession session = mock(DrivingSession.class);
        when(session.getStartedAt()).thenReturn(at(17, 0));
        when(session.getEndedAt()).thenReturn(at(17, 15));
        when(assignment.getDrivingSessions()).thenReturn(List.of(session));

        PassengerRideConfirmation confirmation = confirmation(assignment, at(17, 8));
        when(repository
                .findAllForServiceVerification(
                        7L,
                        DATE.atStartOfDay(ZONE).toInstant(),
                        DATE.plusDays(1).atStartOfDay(ZONE).toInstant()
                ))
                .thenReturn(List.of(confirmation));

        ServiceVerificationService service = service(repository);
        var report = service.build(
                7L,
                DATE,
                demand(List.of(window(17, 0, 17, 15, 48))),
                List.of(assignment),
                at(18, 0)
        );

        ServiceVerificationPeriod period = report.periods().get(0);
        assertThat(period.confidence()).isEqualTo(ServiceVerificationConfidence.HIGH);
        assertThat(period.passengerConfirmations()).isEqualTo(1);
        assertThat(period.activeDrivingMinutes()).isEqualTo(15);
        assertThat(period.needsReview()).isFalse();
        assertThat(period.windows().get(0).drivingCoveragePercent()).isEqualTo(100);
    }

    @Test
    void flagsCompletedMeaningfulDemandWindowWithNoServiceEvidence() {
        PassengerRideConfirmationRepository repository = mock(PassengerRideConfirmationRepository.class);
        DriverAssignment assignment = assignment(42L);
        when(assignment.getDrivingSessions()).thenReturn(List.of());
        when(repository
                .findAllForServiceVerification(
                        7L,
                        DATE.atStartOfDay(ZONE).toInstant(),
                        DATE.plusDays(1).atStartOfDay(ZONE).toInstant()
                ))
                .thenReturn(List.of());

        var report = service(repository).build(
                7L,
                DATE,
                demand(List.of(window(17, 0, 17, 15, 35))),
                List.of(assignment),
                at(18, 0)
        );

        ServiceVerificationPeriod period = report.periods().get(0);
        assertThat(period.confidence()).isEqualTo(ServiceVerificationConfidence.LOW);
        assertThat(period.noEvidenceWindowCount()).isEqualTo(1);
        assertThat(period.needsReview()).isTrue();
        assertThat(period.reviewMessage()).contains("no driving-session or rider-confirmation evidence");
    }

    @Test
    void treatsTwoConsecutiveZeroQrWindowsAsWeakEvidenceNotProofOfMissedService() {
        PassengerRideConfirmationRepository repository = mock(PassengerRideConfirmationRepository.class);
        DriverAssignment assignment = assignment(42L);

        DrivingSession session = mock(DrivingSession.class);
        when(session.getStartedAt()).thenReturn(at(17, 0));
        when(session.getEndedAt()).thenReturn(at(17, 30));
        when(assignment.getDrivingSessions()).thenReturn(List.of(session));
        when(repository
                .findAllForServiceVerification(
                        7L,
                        DATE.atStartOfDay(ZONE).toInstant(),
                        DATE.plusDays(1).atStartOfDay(ZONE).toInstant()
                ))
                .thenReturn(List.of());

        var report = service(repository).build(
                7L,
                DATE,
                demand(List.of(
                        window(17, 0, 17, 15, 30),
                        window(17, 15, 17, 30, 28)
                )),
                List.of(assignment),
                at(18, 0)
        );

        ServiceVerificationPeriod period = report.periods().get(0);
        assertThat(period.confidence()).isEqualTo(ServiceVerificationConfidence.MEDIUM);
        assertThat(period.longestZeroConfirmationStreak()).isEqualTo(2);
        assertThat(period.weakPassengerEvidence()).isTrue();
        assertThat(period.noEvidenceWindowCount()).isZero();
        assertThat(period.needsReview()).isTrue();
    }

    @Test
    void doesNotEvaluateFutureDemandWindows() {
        PassengerRideConfirmationRepository repository = mock(PassengerRideConfirmationRepository.class);
        DriverAssignment assignment = assignment(42L);
        when(assignment.getDrivingSessions()).thenReturn(List.of());
        when(repository
                .findAllForServiceVerification(
                        7L,
                        DATE.atStartOfDay(ZONE).toInstant(),
                        DATE.plusDays(1).atStartOfDay(ZONE).toInstant()
                ))
                .thenReturn(List.of());

        var report = service(repository).build(
                7L,
                DATE,
                demand(List.of(window(17, 0, 17, 15, 40))),
                List.of(assignment),
                at(16, 0)
        );

        ServiceVerificationPeriod period = report.periods().get(0);
        assertThat(period.confidence()).isEqualTo(ServiceVerificationConfidence.UPCOMING);
        assertThat(period.needsReview()).isFalse();
        assertThat(period.evaluatedWindowCount()).isZero();
    }

    private ServiceVerificationService service(PassengerRideConfirmationRepository repository) {
        return new ServiceVerificationService(
                repository,
                10,
                2,
                50,
                ZONE.getId()
        );
    }

    private DriverAssignment assignment(Long id) {
        DriverAssignment assignment = mock(DriverAssignment.class);
        Driver driver = mock(Driver.class);
        when(assignment.getId()).thenReturn(id);
        when(assignment.getPeriod()).thenReturn(ShuttlePeriod.AFTERNOON);
        when(assignment.getScheduledDriver()).thenReturn(driver);
        when(driver.getName()).thenReturn("Alex");
        when(assignment.getActualDriverNames()).thenReturn("Alex");
        return assignment;
    }

    private PassengerRideConfirmation confirmation(
            DriverAssignment assignment,
            Instant submittedAt) {
        PassengerRideConfirmation confirmation = mock(PassengerRideConfirmation.class);
        when(confirmation.getAssignment()).thenReturn(assignment);
        when(confirmation.getSubmittedAt()).thenReturn(submittedAt);
        return confirmation;
    }

    private TransportationDemandAnalysis demand(List<TransportationDemandWindow> eveningWindows) {
        TransportationDemandPeriod morning = TransportationDemandPeriod.empty(
                "morning", "Morning Arrivals", "Morning driving", "", "↑", "arrivals", "Peak"
        );
        TransportationDemandPeriod midday = TransportationDemandPeriod.empty(
                "midday", "Midday Turnover", "Midday coverage", "", "↕", "transitions", "Peak"
        );
        int total = eveningWindows.stream().mapToInt(TransportationDemandWindow::getDemandCount).sum();
        TransportationDemandPeriod evening = new TransportationDemandPeriod(
                "evening",
                "Evening Pickups",
                "Evening driving",
                "",
                "↓",
                "pickups",
                "Peak",
                eveningWindows,
                eveningWindows.get(0).getWindowStart(),
                eveningWindows.get(eveningWindows.size() - 1).getWindowEnd(),
                total,
                eveningWindows.get(0).getWindowStart(),
                eveningWindows.get(0).getWindowEnd(),
                eveningWindows.get(0).getDemandCount()
        );
        return new TransportationDemandAnalysis(
                morning,
                midday,
                evening,
                List.of(),
                0,
                eveningWindows.get(0).getWindowStart(),
                eveningWindows.get(0).getWindowEnd(),
                eveningWindows.get(0).getDemandCount(),
                "Evening Pickups"
        );
    }

    private TransportationDemandWindow window(
            int startHour,
            int startMinute,
            int endHour,
            int endMinute,
            int demand) {
        return new TransportationDemandWindow(
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute),
                0,
                demand,
                demand,
                PickupPriority.HIGH
        );
    }

    private Instant at(int hour, int minute) {
        return DATE.atTime(hour, minute).atZone(ZONE).toInstant();
    }
}
