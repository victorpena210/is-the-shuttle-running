package technology.renatus.routepulse.reporting.workload.view;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import technology.renatus.routepulse.reporting.workload.model.DrivingRecordPage;

/** Maps driving-record page data onto the Thymeleaf model. */
@Component
public class DrivingRecordModelAssembler {

    public void addTo(Model model, DrivingRecordPage page) {
        model.addAttribute("organizationName", page.organizationName());
        model.addAttribute("isOwner", page.owner());
        model.addAttribute("drivers", page.drivers());
        model.addAttribute("records", page.records());
        model.addAttribute("unscheduledRecords", page.unscheduledRecords());
        model.addAttribute("workloadSummary", page.workloadSummary());
        model.addAttribute("selectedDriverSummary", page.selectedDriverSummary());
        model.addAttribute("makeupStatus", page.makeupStatus());
        model.addAttribute("outcomes", page.outcomes());
        model.addAttribute("fromDate", page.fromDate());
        model.addAttribute("toDate", page.toDate());
        model.addAttribute("selectedDriverId", page.selectedDriverId());
        model.addAttribute("selectedView", page.selectedView());
        model.addAttribute("filterError", page.filterError());
    }
}
