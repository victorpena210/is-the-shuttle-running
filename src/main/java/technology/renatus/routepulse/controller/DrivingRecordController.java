package technology.renatus.routepulse.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import technology.renatus.routepulse.model.DriverAssignment;
import technology.renatus.routepulse.model.Organization;
import technology.renatus.routepulse.service.CurrentOrganizationService;
import technology.renatus.routepulse.service.DrivingRecordService;
import technology.renatus.routepulse.service.DriverService;

@Controller
public class DrivingRecordController {

    private final DrivingRecordService
            drivingRecordService;

    private final DriverService driverService;

    private final CurrentOrganizationService
            currentOrganizationService;

    public DrivingRecordController(
            DrivingRecordService drivingRecordService,
            DriverService driverService,
            CurrentOrganizationService currentOrganizationService) {

        this.drivingRecordService =
                drivingRecordService;

        this.driverService =
                driverService;

        this.currentOrganizationService =
                currentOrganizationService;
    }

    @GetMapping("/driving-records")
    public String showDrivingRecords(
            Authentication authentication,

            @RequestParam(
                    name = "from",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate fromDate,

            @RequestParam(
                    name = "to",
                    required = false
            )
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE
            )
            LocalDate toDate,

            @RequestParam(required = false)
            Long driverId,

            Model model) {

        Organization organization =
                currentOrganizationService
                        .requireOrganization(
                                authentication
                        );

        Long organizationId = organization.getId();

        LocalDate currentOperationsDate =
                drivingRecordService
                        .getCurrentOperationsDate();

        if (fromDate == null && toDate == null) {
            toDate = currentOperationsDate;
            fromDate = currentOperationsDate.minusDays(30);
        } else if (fromDate == null) {
            fromDate = toDate;
        } else if (toDate == null) {
            toDate = fromDate;
        }

        List<DriverAssignment> records = List.of();
        String filterError = null;

        try {
            records =
                    drivingRecordService
                            .getDrivingRecords(
                                    organizationId,
                                    fromDate,
                                    toDate,
                                    driverId
                            );
        } catch (IllegalArgumentException exception) {
            filterError = exception.getMessage();
        }

        boolean isOwner =
                authentication
                        .getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals("ROLE_OWNER")
                        );

        model.addAttribute(
                "organizationName",
                organization.getDisplayName()
        );

        model.addAttribute(
                "isOwner",
                isOwner
        );

        model.addAttribute(
                "drivers",
                driverService.getAllDrivers(
                        organizationId
                )
        );

        model.addAttribute(
                "records",
                records
        );

        model.addAttribute(
                "fromDate",
                fromDate
        );

        model.addAttribute(
                "toDate",
                toDate
        );

        model.addAttribute(
                "selectedDriverId",
                driverId
        );

        model.addAttribute(
                "filterError",
                filterError
        );

        return "driving-records";
    }
}