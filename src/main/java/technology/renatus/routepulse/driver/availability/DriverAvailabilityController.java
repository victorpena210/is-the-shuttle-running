package technology.renatus.routepulse.driver.availability;

import jakarta.servlet.http.HttpSession;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.driver.model.Driver;
import technology.renatus.routepulse.driver.service.portal.DriverPortalAccessService;

@Controller
@RequestMapping("/driver/availability")
public class DriverAvailabilityController {
    private record Draft(String token, Long driver, Long org, long version, Instant expires, String source, String digest) implements java.io.Serializable { }
    private final DriverPortalAccessService access;
    private final AvailabilityUpdateService updates;
    private final AvailabilityReviewService review;
    private final ScheduleScreenshotReader reader;
    public DriverAvailabilityController(DriverPortalAccessService access, AvailabilityUpdateService updates,
            AvailabilityReviewService review, ScheduleScreenshotReader reader) {
        this.access=access; this.updates=updates; this.review=review; this.reader=reader;
    }
    @InitBinder("availabilityForm")
    void binding(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(60);
        binder.setAllowedFields("token","confirmed","rows[*].date","rows[*].start","rows[*].end","rows[*].off");
    }
    @GetMapping
    public String page(Authentication auth, HttpSession session, Model model) {
        var driver=access.requireCurrentDriver(auth); var form=new AvailabilityForm();
        form.getRows().add(new AvailabilityForm.Row());
        newDraft(driver,form,"MANUAL",null,session);
        return render(driver,form,model);
    }
    @PostMapping("/preview")
    public String preview(Authentication auth, @RequestParam("screenshots") List<MultipartFile> files,
            @RequestParam int year, HttpSession session, Model model) {
        var driver=access.requireCurrentDriver(auth); access.requireActiveDriver(driver);
        var form=new AvailabilityForm(); String digest=null;
        try {
            if (year < updates.today().getYear() || year > updates.today().plusDays(366).getYear())
                throw new IllegalArgumentException("Choose the year shown in your schedule.");
            if (files.isEmpty() || files.size()>3 || files.stream().mapToLong(MultipartFile::getSize).sum()>12*1024*1024)
                throw new IllegalArgumentException("Upload one to three screenshots, up to 5 MB each and 12 MB total.");
            StringBuilder raw=new StringBuilder(); List<String> hashes=new ArrayList<>(); Set<String> warnings=new LinkedHashSet<>();
            for (var file:files) {
                var read=reader.read(file); hashes.add(read.digest()); raw.append(read.text()).append("\n");
                var parsed=ScheduleScreenshotParser.parse(read.text(),year);
                parsed.rows().forEach(r -> form.getRows().add(new AvailabilityForm.Row(r))); warnings.addAll(parsed.warnings());
            }
            // Overlapping screenshots commonly repeat the same shift.
            var unique = new java.util.LinkedHashMap<String, AvailabilityForm.Row>();
            for (var row : form.getRows()) unique.putIfAbsent(row.getDate()+"|"+row.getStart()+"|"+row.getEnd()+"|"+row.isOff(),row);
            form.setRows(new java.util.ArrayList<>(unique.values()));
            if (form.getRows().size()>60) throw new IllegalArgumentException("Use at most 60 shift rows per update.");
            digest=String.join(",",hashes);
            model.addAttribute("ocrText",raw.toString()); model.addAttribute("warnings",warnings);
        } catch (IllegalArgumentException ex) { model.addAttribute("errorMessage",ex.getMessage()); }
        if (form.getRows().isEmpty()) form.getRows().add(new AvailabilityForm.Row());
        newDraft(driver,form,digest==null ? "MANUAL" : "SCREENSHOT",digest,session);
        return render(driver,form,model);
    }
    @PostMapping("/confirm")
    public String confirm(Authentication auth, @ModelAttribute("availabilityForm") AvailabilityForm form,
            BindingResult binding, HttpSession session, Model model, RedirectAttributes redirect) {
        var driver=access.requireCurrentDriver(auth);
        try {
            if (binding.hasErrors()) throw new IllegalArgumentException("Check the dates and times in your update.");
            Object value=session.getAttribute("availability-draft");
            if (!(value instanceof Draft draft) || !Objects.equals(draft.token(),form.getToken())
                    || !draft.driver().equals(driver.getId()) || !draft.org().equals(driver.getOrganization().getId())
                    || draft.expires().isBefore(Instant.now()))
                throw new IllegalArgumentException("This draft expired or another draft was opened. Start a new schedule update.");
            int count=updates.confirm(driver,draft.version(),form,draft.source(),draft.digest(),auth.getName());
            session.removeAttribute("availability-draft");
            redirect.addFlashAttribute("successMessage",count+" date(s) updated. Your manager can review affected driving assignments.");
            return "redirect:/driver/availability";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage",ex.getMessage()); return render(driver,form,model);
        }
    }
    private void newDraft(Driver driver, AvailabilityForm form, String source, String digest, HttpSession session) {
        String token=UUID.randomUUID().toString(); form.setToken(token);
        session.setAttribute("availability-draft",new Draft(token,driver.getId(),driver.getOrganization().getId(),
                updates.version(driver),Instant.now().plusSeconds(1800),source,digest));
    }
    private String render(Driver driver, AvailabilityForm form, Model model) {
        model.addAttribute("availabilityForm",form); model.addAttribute("driverPage","schedule");
        model.addAttribute("driverName",driver.getName()); model.addAttribute("organizationName",driver.getOrganization().getDisplayName());
        model.addAttribute("today",updates.today()); model.addAttribute("operationsZone",updates.zoneName());
        model.addAttribute("updates",review.updates(driver.getOrganization().getId(),driver.getId()));
        model.addAttribute("conflicts",review.conflicts(driver.getOrganization().getId(),driver.getId()));
        return "driver/availability";
    }
}
