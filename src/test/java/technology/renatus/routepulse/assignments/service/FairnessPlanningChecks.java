package technology.renatus.routepulse.assignments.service;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import technology.renatus.routepulse.assignments.domain.*;
import technology.renatus.routepulse.assignments.model.*;
import technology.renatus.routepulse.assignments.repository.*;
import technology.renatus.routepulse.assignments.service.planning.DriverScheduleSelector;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.calendar.domain.*;
import technology.renatus.routepulse.calendar.service.*;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.repository.DriverRepository;
import technology.renatus.routepulse.driving.domain.DrivingActionMethod;
import technology.renatus.routepulse.driving.model.DrivingSession;
import technology.renatus.routepulse.driving.repository.DrivingSessionRepository;
import technology.renatus.routepulse.organization.domain.OrganizationStatus;
import technology.renatus.routepulse.organization.model.Organization;
import technology.renatus.routepulse.organization.repository.OrganizationRepository;
import technology.renatus.routepulse.scheduling.domain.*;
import technology.renatus.routepulse.scheduling.model.ScheduleImport;
import technology.renatus.routepulse.scheduling.planning.*;
import technology.renatus.routepulse.scheduling.repository.UploadedShiftRepository;

/** Repository fakes exercise real planning/domain code, without a database or calendar network calls. */
public class FairnessPlanningChecks {
    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDate FIRST = TODAY.plusDays(1);
    private static final UserAccount ACTOR = new UserAccount("test@example.com", "hash", "Test", "Manager", true, true);
    private static int checks;

    public static void main(String[] args) {
        runChecks();
    }

    static void runChecks() {
        checks = 0;
        run("pending earlier assignments reserve planned minutes", () -> {
            Fixture f = new Fixture();
            Driver first = f.driver(f.rank(FIRST.plusDays(1)).get(0).driverId());
            f.add(FIRST, ShuttlePeriod.MORNING, first, 9, 12);
            check(!f.rank(FIRST.plusDays(1)).get(0).driverId().equals(first.getId()), "planned workload ignored");
        });
        run("equal workload ties rotate instead of repeating alphabetical order", () -> {
            Fixture f = new Fixture();
            Set<Long> winners = new HashSet<>();
            for (int day = 0; day < 3; day++) winners.add(f.rank(FIRST.plusDays(day)).get(0).driverId());
            check(winners.size() == 3, "all three drivers should lead once");
        });
        run("actual and planned minutes are not double counted", () -> {
            Fixture f = new Fixture();
            DriverAssignment driven = f.add(FIRST, ShuttlePeriod.MORNING, f.drivers.get(0), 9, 11);
            f.complete(driven, f.drivers.get(0), 120);
            f.add(FIRST, ShuttlePeriod.AFTERNOON, f.drivers.get(1), 17, 20);
            List<DriverSchedule> ranked = f.workload.rankEligibleDrivers(1L, FIRST.plusDays(1), f.schedules(FIRST).subList(0, 2));
            check(ranked.get(0).driverId().equals(f.drivers.get(0).getId()), "120 actual minutes counted as 240");
        });
        run("fill-in actual minutes belong to the actual driver", () -> {
            Fixture f = new Fixture();
            DriverAssignment driven = f.add(TODAY.minusDays(1), ShuttlePeriod.MORNING, f.drivers.get(0), 9, 11);
            f.complete(driven, f.drivers.get(1), 120);
            check(f.workload.getDriverSnapshot(1L, f.drivers.get(0).getId(), FIRST).actualMinutes() == 0, "scheduled driver charged");
            check(f.workload.getDriverSnapshot(1L, f.drivers.get(1).getId(), FIRST).actualMinutes() == 120, "fill-in not credited");
        });
        run("missed, excused and cancelled duties do not reserve future workload", () -> {
            for (AssignmentOutcome outcome : List.of(AssignmentOutcome.MISSED, AssignmentOutcome.EXCUSED, AssignmentOutcome.CANCELLED)) {
                Fixture f = new Fixture();
                Long winner = f.rank(FIRST.plusDays(1)).get(0).driverId();
                f.add(FIRST, ShuttlePeriod.MORNING, f.driver(winner), 9, 21).updateDerivedOutcome(outcome);
                check(f.rank(FIRST.plusDays(1)).get(0).driverId().equals(winner), outcome + " counted as future work");
            }
        });
        run("make-up priority reserves a turn without falsely completing the make-up", () -> {
            Fixture f = new Fixture();
            Driver driver = f.drivers.get(0);
            DriverAssignment prior = f.add(TODAY.minusDays(1), ShuttlePeriod.MORNING, driver, 9, 14);
            f.complete(prior, driver, 300);
            f.events.add(new DriverWorkloadEvent(driver, prior, null, DriverWorkloadEventType.MAKEUP_REQUIRED,
                    "Missed duty", ACTOR, TODAY.minusDays(1).atStartOfDay(ZONE).toInstant()));
            check(f.rank(FIRST).get(0).driverId().equals(driver.getId()), "make-up must take priority");
            f.add(FIRST, ShuttlePeriod.MORNING, driver, 9, 12);
            check(!f.rank(FIRST.plusDays(1)).get(0).driverId().equals(driver.getId()), "same make-up assigned repeatedly");
            check(f.events.size() == 1 && f.events.get(0).getEventType() == DriverWorkloadEventType.MAKEUP_REQUIRED,
                    "planning must not complete make-ups");
        });
        run("three days rotate all periods and ignore tab-opening order", () -> {
            Fixture early = new Fixture();
            early.planner.getOrCreateAssignments(1L, FIRST);
            Fixture late = new Fixture();
            late.planner.getOrCreateAssignments(1L, FIRST.plusDays(2));
            check(early.signature().equals(late.signature()), "date-view order changed assignments");
            check(early.planningDates.equals(List.of(FIRST, FIRST.plusDays(1), FIRST.plusDays(2))), "planning not chronological");
            check(early.assignments.size() == 9, "expected all three periods on all three days");
            for (ShuttlePeriod period : ShuttlePeriod.values()) {
                check(early.assignments.stream().filter(a -> a.getPeriod() == period)
                        .map(a -> a.getScheduledDriver().getId()).distinct().count() == 3, "period did not rotate: " + period);
            }
        });
        run("unequal period lengths balance total planned minutes", () -> {
            Fixture f = new Fixture();
            f.longPeriods = true;
            f.planner.getOrCreateAssignments(1L, FIRST);
            List<Long> totals = f.drivers.stream().map(driver -> f.assignments.stream()
                    .filter(a -> a.getScheduledDriver().getId().equals(driver.getId()))
                    .mapToLong(DriverAssignment::getScheduledMinutes).sum()).toList();
            check(totals.stream().mapToLong(Long::longValue).sum() == 1440, "planned coverage minutes lost");
            // Duties are indivisible: balance within one longest duty, rather than promise identical totals.
            check(Collections.max(totals) - Collections.min(totals) <= 240,
                    "workload spread exceeds one longest duty: " + totals);
        });
        run("repeat planning preserves IDs, assignments and change timestamps", () -> {
            Fixture f = new Fixture();
            DriverAssignment original = f.add(FIRST, ShuttlePeriod.MORNING, f.drivers.get(0), 9, 10);
            Long id = original.getId();
            Driver firstSuggestion = original.getOriginallySuggestedDriver();
            f.planner.getOrCreateAssignments(1L, FIRST);
            String signature = f.signature();
            Map<Long, Instant> timestamps = new HashMap<>();
            f.assignments.forEach(a -> timestamps.put(a.getId(), a.getChangedAt()));
            f.planner.getOrCreateAssignments(1L, FIRST.plusDays(2));
            check(signature.equals(f.signature()), "refresh shuffled drivers or IDs");
            for (DriverAssignment a : f.assignments) check(Objects.equals(timestamps.get(a.getId()), a.getChangedAt()), "refresh changed audit timestamp");
            check(original.getId().equals(id) && original.getOriginallySuggestedDriver() == firstSuggestion, "original audit lost");
        });
        run("manual, override, reviewed, started and historical assignments are protected", () -> {
            Fixture f = new Fixture();
            LocalDateTime now = TODAY.atTime(8, 0);
            DriverAssignment manual = DriverAssignment.manual(FIRST, ShuttlePeriod.MORNING, f.drivers.get(0), "Manager");
            check(!manual.canAutomaticallyRebalance(now), "manual mutable");
            DriverAssignment override = f.add(FIRST, ShuttlePeriod.MORNING, f.drivers.get(0), 9, 10);
            override.changeDriver(f.drivers.get(1), "Manager", ACTOR);
            check(!override.canAutomaticallyRebalance(now), "override mutable");
            DriverAssignment edited = f.add(FIRST, ShuttlePeriod.MIDDAY, f.drivers.get(0), 13, 14);
            edited.protectManualTimeEdit();
            check(!edited.canAutomaticallyRebalance(now), "time edit mutable");
            DriverAssignment reviewed = f.add(FIRST, ShuttlePeriod.AFTERNOON, f.drivers.get(0), 18, 19);
            set(reviewed, "scheduleReviewedAt", Instant.now());
            check(!reviewed.canAutomaticallyRebalance(now), "review mutable");
            DriverAssignment started = f.add(FIRST.plusDays(1), ShuttlePeriod.MORNING, f.drivers.get(0), 9, 10);
            f.complete(started, f.drivers.get(0), 15);
            check(!started.canAutomaticallyRebalance(now), "session history mutable");
            DriverAssignment historic = f.add(TODAY.minusDays(1), ShuttlePeriod.MORNING, f.drivers.get(0), 9, 10);
            check(!historic.canAutomaticallyRebalance(now), "history mutable");
            DriverAssignment overdue = f.add(TODAY, ShuttlePeriod.MORNING, f.drivers.get(0), 7, 9);
            check(!overdue.canAutomaticallyRebalance(now), "already-due assignment mutable");
            String signature = f.signature();
            f.planner.getOrCreateAssignments(1L, FIRST);
            for (String row : signature.split("\n")) check(f.signature().contains(row), "protected assignment changed: " + row);
        });
        run("newly available drivers replace stale automatic coverage", () -> {
            Fixture f = new Fixture();
            for (LocalDate date : f.dates) for (ShuttlePeriod period : ShuttlePeriod.values()) {
                f.add(date, period, f.drivers.get(0), period == ShuttlePeriod.MORNING ? 9 : period == ShuttlePeriod.MIDDAY ? 13 : 18,
                        period == ShuttlePeriod.MORNING ? 10 : period == ShuttlePeriod.MIDDAY ? 14 : 19);
            }
            Set<Long> ids = new HashSet<>(f.assignments.stream().map(DriverAssignment::getId).toList());
            f.planner.getOrCreateAssignments(1L, FIRST);
            check(f.assignments.stream().map(a -> a.getScheduledDriver().getId()).distinct().count() == 3, "stale drivers not rebalanced");
            check(ids.equals(new HashSet<>(f.assignments.stream().map(DriverAssignment::getId).toList())), "rebalance replaced records");
        });
        run("short Sunday shift cannot receive evening coverage", () -> {
            Fixture f = new Fixture();
            f.shortLastDay = true;
            f.planner.getOrCreateAssignments(1L, FIRST.plusDays(2));
            DriverAssignment evening = f.assignments.stream().filter(a -> a.getAssignmentDate().equals(FIRST.plusDays(2))
                    && a.getPeriod() == ShuttlePeriod.AFTERNOON).findFirst().orElseThrow();
            check(!evening.getScheduledDriver().getId().equals(f.drivers.get(2).getId()), "9–3 driver assigned evening");
        });
        run("short-shift driver takes morning so longer shifts cover all later demand", () -> {
            Fixture f = new Fixture();
            f.shortLastDay = true;
            f.longPeriods = true;
            f.longMidday = true;
            f.planner.getOrCreateAssignments(1L, FIRST.plusDays(2));
            List<DriverAssignment> sunday = f.assignments.stream()
                    .filter(a -> a.getAssignmentDate().equals(FIRST.plusDays(2))).toList();
            DriverAssignment morning = sunday.stream().filter(a -> a.getPeriod() == ShuttlePeriod.MORNING).findFirst().orElseThrow();
            check(morning.getScheduledDriver().getId().equals(3L), "short-shift driver was left for an unsuitable later duty");
            check(sunday.stream().mapToLong(DriverAssignment::getScheduledMinutes).sum() == 660,
                    "coverage was shortened even though the team could cover all periods");
        });
        run("full coverage takes priority over partial overlap", () -> {
            DriverSchedule shortShift = new DriverSchedule(1L, "1", "Short", FIRST, LocalTime.of(9, 0), LocalTime.of(18, 0));
            DriverSchedule fullShift = new DriverSchedule(2L, "2", "Full", FIRST, LocalTime.of(9, 0), LocalTime.of(21, 0));
            DriverSchedule chosen = new DriverScheduleSelector().chooseForWindow(List.of(shortShift, fullShift),
                    Map.of(1L, List.of(shortShift), 2L, List.of(fullShift)), LocalTime.of(17, 0), LocalTime.of(21, 0), Set.of());
            check(chosen.driverId().equals(2L), "fairness shortened evening when full coverage was available");
        });
        run("closed and unknown dates cannot generate automatic coverage", () -> {
            Fixture f = new Fixture();
            f.closed.add(FIRST);
            f.unknown.add(FIRST.plusDays(1));
            f.planner.getOrCreateAssignments(1L, FIRST.plusDays(2));
            check(f.assignments.size() == 3 && f.assignments.stream().allMatch(a -> a.getAssignmentDate().equals(FIRST.plusDays(2))),
                    "calendar gate bypassed");
        });
        run("other organizations do not affect workload", () -> {
            Fixture f = new Fixture();
            Long winner = f.rank(FIRST.plusDays(1)).get(0).driverId();
            DriverAssignment foreign = f.add(FIRST, ShuttlePeriod.MORNING, f.driver(winner), 9, 21);
            set(foreign, "organization", withId(new Organization("Other", "Other", "other", OrganizationStatus.ACTIVE, null, null), 2L));
            check(f.rank(FIRST.plusDays(1)).get(0).driverId().equals(winner), "organization workload leaked");
        });
        System.out.println("PASS: " + checks + " fairness planning checks");
    }

    private static void run(String name, Runnable test) {
        try { test.run(); checks++; System.out.println("PASS: " + name); }
        catch (Throwable failure) { throw new AssertionError(name, failure); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static <T> T withId(T object, Long id) { set(object, "id", id); return object; }
    private static void set(Object object, String name, Object value) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked")
    private static <T> T repository(Class<T> type, BiFunction<String, Object[], Object> answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> answer.apply(method.getName(), args == null ? new Object[0] : args));
    }

    private static class Fixture {
        final Organization organization = withId(new Organization("Test", "Test", "test", OrganizationStatus.ACTIVE, null, null), 1L);
        final List<Driver> drivers = List.of(withId(new Driver(organization, "1", "Jody", true), 1L),
                withId(new Driver(organization, "2", "Jonathan", true), 2L), withId(new Driver(organization, "3", "Victor", true), 3L));
        final List<LocalDate> dates = List.of(FIRST.plusDays(2), FIRST, FIRST.plusDays(1));
        final List<LocalDate> planningDates = new ArrayList<>();
        final Set<LocalDate> closed = new HashSet<>(), unknown = new HashSet<>();
        final List<DriverAssignment> assignments = new ArrayList<>();
        final List<DriverWorkloadEvent> events = new ArrayList<>();
        final List<DrivingSession> sessions = new ArrayList<>();
        boolean shortLastDay, longPeriods, longMidday;
        final DriverAssignmentRepository assignmentRepository = repository(DriverAssignmentRepository.class, (method, args) -> {
            if (method.equals("flush")) return null;
            if (method.equals("saveAll")) {
                for (Object value : (Iterable<?>) args[0]) {
                    DriverAssignment a = (DriverAssignment) value;
                    withId(a, (long) assignments.size() + 1); assignments.add(a);
                }
                return args[0];
            }
            if (method.equals("findAllByOrganization_IdAndAssignmentDateBetween")) return assignments.stream()
                    .filter(a -> a.getOrganization().getId().equals(args[0]) && !a.getAssignmentDate().isBefore((LocalDate) args[1])
                            && !a.getAssignmentDate().isAfter((LocalDate) args[2])).toList();
            if (method.equals("findAllForUpdateByOrganizationAndDate") || method.equals("findAllByOrganization_IdAndAssignmentDate"))
                return assignments.stream().filter(a -> a.getOrganization().getId().equals(args[0]) && a.getAssignmentDate().equals(args[1])).toList();
            throw new AssertionError("Unexpected assignment repository call: " + method);
        });
        final DrivingSessionRepository sessionRepository = repository(DrivingSessionRepository.class, (method, args) -> sessions.stream()
                .filter(s -> s.getOrganization().getId().equals(args[0]) && s.isCompleted()
                        && (method.contains("AssignmentIsNull")
                            ? s.isUnscheduled() && s.getStartedAt().isBefore((Instant) args[1])
                            : !s.isUnscheduled() && s.getAssignment().getAssignmentDate().isBefore((LocalDate) args[1]))).toList());
        final DriverWorkloadEventRepository eventRepository = repository(DriverWorkloadEventRepository.class, (method, args) -> events.stream()
                .filter(e -> e.getOrganization().getId().equals(args[0]) && e.getCreatedAt().isBefore((Instant) args[1])).toList());
        final DriverWorkloadService workload = new DriverWorkloadService(sessionRepository, eventRepository, assignmentRepository, ZONE.getId());
        final AssignmentPlanningService planner;

        Fixture() {
            DriverRepository driverRepository = repository(DriverRepository.class, (method, args) ->
                    args[1].equals(1L) ? Optional.of(driver((Long) args[0])) : Optional.empty());
            UploadedShiftRepository shifts = repository(UploadedShiftRepository.class, (method, args) ->
                    method.equals("findAvailableDates") ? dates : Optional.empty());
            OrganizationRepository organizations = repository(OrganizationRepository.class, (method, args) -> Optional.of(organization));
            DriverScheduleService schedules = new DriverScheduleService(null, null) {
                @Override public DriverScheduleResult getDriverSchedulesForDate(Long organizationId, LocalDate date) {
                    planningDates.add(date); return new DriverScheduleResult(schedules(date), List.of());
                }
            };
            MorningDrivingPlanningService morning = new MorningDrivingPlanningService(null, "09:00", "12:00", 15, 15) {
                @Override public MorningDrivingPlan getPlanForDate(Long organizationId, LocalDate date) {
                    return new MorningDrivingPlan(List.of(), LocalTime.of(9, 0), LocalTime.of(9, 0), LocalTime.of(9, 0),
                            LocalTime.of(longPeriods ? 12 : 10, 0), 1);
                }
            };
            TransportationDemandService demand = new TransportationDemandService(null, morning, "09:00", "12:00", "17:00", "21:00", 15, 15) {
                @Override public TransportationDemandAnalysis getDemandForDate(Long organizationId, LocalDate date) {
                    return new TransportationDemandAnalysis(period(9, longPeriods ? 12 : 10), period(13, longMidday ? 17 : 14),
                            period(longPeriods ? 17 : 18, longPeriods ? 21 : 19), List.of(), 0, null, null, 0, null);
                }
            };
            ParkOperatingDayService operatingDays = new ParkOperatingDayService(date -> new ParkOperatingDay(date,
                    closed.contains(date) ? ParkOperatingStatus.CLOSED : unknown.contains(date) ? ParkOperatingStatus.UNKNOWN : ParkOperatingStatus.OPEN,
                    LocalTime.of(9, 0), LocalTime.of(21, 0), false, "TEST", null));
            ParkCalendarOverrideService overrides = new ParkCalendarOverrideService(null, null, null) {
                @Override public Optional<technology.renatus.routepulse.calendar.model.ParkCalendarOverride> findLatest(Long org, LocalDate date) {
                    return Optional.empty();
                }
            };
            planner = new AssignmentPlanningService(assignmentRepository, driverRepository, schedules, shifts, workload,
                    morning, demand, 0, organizations, operatingDays, overrides, ZONE.getId());
            set(planner, "clock", Clock.fixed(TODAY.atTime(8, 0).atZone(ZONE).toInstant(), ZONE));
        }
        Driver driver(Long id) { return drivers.stream().filter(d -> d.getId().equals(id)).findFirst().orElseThrow(); }
        List<DriverSchedule> schedules(LocalDate date) {
            return drivers.stream().map(d -> new DriverSchedule(d.getId(), d.getEmployeeId(), d.getName(), date,
                    LocalTime.of(9, 0), LocalTime.of(shortLastDay && date.equals(FIRST.plusDays(2)) && d.getId().equals(3L) ? 15 : 21, 0))).toList();
        }
        List<DriverSchedule> rank(LocalDate date) { return workload.rankEligibleDrivers(1L, date, schedules(date)); }
        DriverAssignment add(LocalDate date, ShuttlePeriod period, Driver driver, int start, int end) {
            DriverAssignment a = withId(DriverAssignment.automatic(date, period, driver), (long) assignments.size() + 1);
            a.updateScheduledTimes(LocalTime.of(start, 0), LocalTime.of(end, 0));
            a.replacePlannedDrivingWindows(List.of(new PlannedDrivingWindow(LocalTime.of(start, 0), LocalTime.of(end, 0))));
            assignments.add(a); return a;
        }
        void complete(DriverAssignment assignment, Driver actual, long minutes) {
            Instant start = assignment.getAssignmentDate().atTime(9, 0).atZone(ZONE).toInstant();
            DrivingSession session = DrivingSession.start(assignment, actual, start, ACTOR, DrivingActionMethod.MANAGER);
            session.end(start.plusSeconds(minutes * 60), ACTOR, DrivingActionMethod.MANAGER);
            assignment.registerDrivingSession(session); sessions.add(session);
        }
        String signature() {
            return assignments.stream().sorted(Comparator.comparing(DriverAssignment::getId)).map(a -> a.getId() + ":" + a.getAssignmentDate()
                    + ":" + a.getPeriod() + ":" + a.getScheduledDriver().getId() + ":" + a.getScheduledMinutes()).reduce((a, b) -> a + "\n" + b).orElse("");
        }
    }
    private static TransportationDemandPeriod period(int start, int end) {
        LocalTime from = LocalTime.of(start, 0), to = LocalTime.of(end, 0);
        return new TransportationDemandPeriod("test", "test", "test", "test", "", "", "",
                List.of(new TransportationDemandWindow(from, to, 1, 0, 1, null)), from, to, 1, from, to, 1);
    }
}
