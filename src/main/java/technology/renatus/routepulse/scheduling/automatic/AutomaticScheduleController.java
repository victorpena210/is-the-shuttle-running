package technology.renatus.routepulse.scheduling.automatic;

import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.calendar.service.*;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;
import technology.renatus.routepulse.organization.service.CurrentOrganizationService;
import technology.renatus.routepulse.wardrobe.*;

@Controller
public class AutomaticScheduleController {
    private final AutomaticScheduleService schedules;
    private final AutomaticScheduleStore store;
    private final CurrentOrganizationService current;
    private final DriverPortalAccessService drivers;
    private final WardrobeHoursService wardrobe;
    private final ParkOperatingDayService calendar;
    private final ParkCalendarOverrideService overrides;
    private final technology.renatus.routepulse.pickup.PickupAlerts pickups;
    public AutomaticScheduleController(AutomaticScheduleService schedules,AutomaticScheduleStore store,CurrentOrganizationService current,
            DriverPortalAccessService drivers,WardrobeHoursService wardrobe,ParkOperatingDayService calendar,ParkCalendarOverrideService overrides, technology.renatus.routepulse.pickup.PickupAlerts pickups) {
        this.schedules=schedules;this.store=store;this.current=current;this.drivers=drivers;this.wardrobe=wardrobe;this.calendar=calendar;this.overrides=overrides;this.pickups=pickups;
    }
    @GetMapping("/driving-schedule")
    public String manager(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,Model model) {
        var org=current.requireOrganization(auth); var from=date==null?schedules.today():date;
        model.addAttribute("days",store.manager(org.getId(),from,from.plusDays(6),auth.getName()));
        addPickups(model,org.getId(),from);
        return page(model,from,true,org.getDisplayName());
    }
    @GetMapping("/driver/driving-schedule")
    public String driver(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,Model model) {
        var driver=drivers.requireCurrentDriver(auth); drivers.requireActiveDriver(driver); var from=date==null?schedules.today():date;
        model.addAttribute("days",store.driver(driver.getOrganization().getId(),driver.getId(),from,from.plusDays(6)));
        model.addAttribute("currentDriverId",driver.getId());
        addPickups(model,driver.getOrganization().getId(),from);
        return page(model,from,false,driver.getOrganization().getDisplayName());
    }
    private void addPickups(Model model,Long org,LocalDate from) {
        var alerts=pickups.forDates(org,from.minusDays(1),from.plusDays(7),Instant.now());
        var bySlot=new java.util.HashMap<AutomaticScheduleDay.Slot,java.util.List<technology.renatus.routepulse.pickup.PickupAlerts.Alert>>();
        for(var alert:alerts) for(var slot:alert.related()) bySlot.computeIfAbsent(slot,key->new java.util.ArrayList<>()).add(alert);
        var uncovered=new java.util.HashMap<LocalDate,java.util.List<technology.renatus.routepulse.pickup.PickupAlerts.Alert>>();
        for(var alert:alerts) if(alert.pending() && alert.needsCoverage())
            uncovered.computeIfAbsent(alert.row().clockOut().toLocalDate(),key->new java.util.ArrayList<>()).add(alert);
        model.addAttribute("pickupsBySlot",bySlot);model.addAttribute("uncoveredPickups",uncovered);
    }
    private String page(Model model,LocalDate from,boolean manager,String name) {
        model.addAttribute("driverPage","assignments");
        model.addAttribute("managerView",manager);model.addAttribute("organizationName",name);model.addAttribute("from",from);
        model.addAttribute("today",schedules.today());model.addAttribute("operationsZone",schedules.zoneName());
        model.addAttribute("now",LocalDateTime.now(ZoneId.of(schedules.zoneName())));
        model.addAttribute("scheduleUrl",manager?"/driving-schedule":"/driver/driving-schedule");
        return "demand/driving-schedule";
    }
    @PostMapping("/driving-schedule/refresh")
    public String refresh(Authentication auth,RedirectAttributes redirect) {
        schedules.refresh(current.requireOrganizationId(auth),auth.getName());
        redirect.addFlashAttribute("successMessage","Driving assignments updated for the next 28 days.");
        return "redirect:/driving-schedule";
    }
    @PostMapping("/driving-schedule/hours")
    public String hours(Authentication auth,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.TIME) LocalTime opening,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.TIME) LocalTime closing,RedirectAttributes redirect) {
        var org=current.requireOrganizationId(auth);
        try {
            wardrobe.save(org,WardrobeHoursRule.Scope.DAY,"Day override",date,date,opening,closing,null,auth.getName());
            schedules.refresh(org,auth.getName());
            redirect.addFlashAttribute("successMessage","Wardrobe hours and upcoming assignments updated.");
        } catch(IllegalArgumentException|IllegalStateException error){redirect.addFlashAttribute("errorMessage",error.getMessage());}
        redirect.addAttribute("date",date.toString());return "redirect:/driving-schedule";
    }
    @PostMapping("/driving-schedule/confirm-open")
    public String confirm(Authentication auth,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String reason,RedirectAttributes redirect) {
        var org=current.requireOrganizationId(auth);
        try {
            if(date.isBefore(schedules.today())||date.isAfter(schedules.today().plusDays(27))) throw new IllegalArgumentException("Choose a date in the next 28 days.");
            overrides.confirmParkOpen(org,schedules.operatingDay(date),auth.getName(),reason);
            schedules.refresh(org,auth.getName());
            redirect.addFlashAttribute("successMessage","Park opening confirmed. Set wardrobe hours if they are not already saved.");
        } catch(IllegalArgumentException|IllegalStateException error){redirect.addFlashAttribute("errorMessage",error.getMessage());}
        redirect.addAttribute("date",date.toString());return "redirect:/driving-schedule";
    }
}
