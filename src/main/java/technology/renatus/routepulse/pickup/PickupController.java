package technology.renatus.routepulse.pickup;

import java.time.LocalDate;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PickupController {
    private final PickupStore store;private final PickupCalendarService calendars;
    public PickupController(PickupStore store,PickupCalendarService calendars){this.store=store;this.calendars=calendars;}
    @GetMapping({"/pickup-assistance","/driver/pickup-assistance"})
    public String page(Authentication auth,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            Model model,HttpServletResponse response) {
        PickupStore.Page page;
        try {page=store.page(auth,date);}catch(IllegalArgumentException exception){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,exception.getMessage());}
        response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");
        model.addAttribute("page",page);model.addAttribute("organizationName",page.organizationName());model.addAttribute("managerView",page.manager());
        model.addAttribute("driverPage","pickup-assistance");model.addAttribute("pageUrl",page.manager()?"/pickup-assistance":"/driver/pickup-assistance");
        return "pickup/index";
    }
    @PostMapping("/pickup-assistance/save")
    public String save(Authentication auth,@RequestParam(required=false) Long id,@RequestParam String name,
            @RequestParam(defaultValue="") String location,@RequestParam(defaultValue="") String subscriptionUrl,RedirectAttributes redirect) {
        try {
            if(id!=null && subscriptionUrl.isBlank()) store.details(auth,id,name,location);
            else calendars.connect(auth,id,name,location,subscriptionUrl);
            redirect.addFlashAttribute("successMessage","Pickup details saved. Connected calendars refresh every 15 minutes.");
        }catch(IllegalArgumentException|IllegalStateException exception){redirect.addFlashAttribute("errorMessage",exception.getMessage());}
        return "redirect:/pickup-assistance";
    }
    @PostMapping("/pickup-assistance/{id}/refresh")
    public String refresh(Authentication auth,@PathVariable Long id,RedirectAttributes redirect) {
        try {
            boolean ok=calendars.refresh(auth,id);
            redirect.addFlashAttribute(ok?"successMessage":"errorMessage",ok?"Calendar refreshed.":"Calendar refresh failed. The last saved schedule is still shown; confirm with the rider.");
        }catch(IllegalArgumentException|IllegalStateException exception){redirect.addFlashAttribute("errorMessage",exception.getMessage());}
        return "redirect:/pickup-assistance";
    }
    @PostMapping("/pickup-assistance/{id}/active")
    public String active(Authentication auth,@PathVariable Long id,@RequestParam boolean active,RedirectAttributes redirect) {
        store.active(auth,id,active);redirect.addFlashAttribute("successMessage",active?"Rider resumed. Calendar refresh is queued.":"Rider paused and hidden from the driver pickup list.");
        return "redirect:/pickup-assistance";
    }
}
