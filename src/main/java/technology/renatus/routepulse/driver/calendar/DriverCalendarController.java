package technology.renatus.routepulse.driver.calendar;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DriverCalendarController {
    private final DriverCalendarStore store;private final DriverCalendarService sync;
    public DriverCalendarController(DriverCalendarStore store,DriverCalendarService sync){this.store=store;this.sync=sync;}
    @GetMapping({"/driver/calendar","/drivers/{id}/calendar"})
    public String page(Authentication auth,@PathVariable(required=false) Long id,Model model) {
        var page=store.page(auth,id);model.addAttribute("calendar",page);model.addAttribute("organizationName",page.organizationName());
        model.addAttribute("managerView",id!=null);model.addAttribute("calendarUrl",url(id));return "driver/calendar";
    }
    @PostMapping({"/driver/calendar","/drivers/{id}/calendar"})
    public String connect(Authentication auth,@PathVariable(required=false) Long id,@RequestParam String subscriptionUrl,RedirectAttributes redirect) {
        try {sync.connect(auth,id,subscriptionUrl);redirect.addFlashAttribute("successMessage","UKG calendar connected. Availability refreshes automatically every 15 minutes.");}
        catch(IllegalArgumentException|IllegalStateException exception){redirect.addFlashAttribute("errorMessage",exception.getMessage());}
        return "redirect:"+url(id);
    }
    @PostMapping({"/driver/calendar/refresh","/drivers/{id}/calendar/refresh"})
    public String refresh(Authentication auth,@PathVariable(required=false) Long id,RedirectAttributes redirect) {
        try {sync.refresh(auth,id);redirect.addFlashAttribute("successMessage","Calendar refresh finished. Check the connection status below.");}
        catch(IllegalArgumentException|IllegalStateException exception){redirect.addFlashAttribute("errorMessage",exception.getMessage());}
        return "redirect:"+url(id);
    }
    @PostMapping({"/driver/calendar/disconnect","/drivers/{id}/calendar/disconnect"})
    public String disconnect(Authentication auth,@PathVariable(required=false) Long id,RedirectAttributes redirect) {
        store.disconnect(auth,id);redirect.addFlashAttribute("successMessage","UKG disconnected. Suggestions will use this driver's uploaded schedule again.");return "redirect:"+url(id);
    }
    private String url(Long id){return id==null?"/driver/calendar":"/drivers/"+id+"/calendar";}
}
