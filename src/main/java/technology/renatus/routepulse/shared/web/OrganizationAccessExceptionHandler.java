package technology.renatus.routepulse.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import technology.renatus.routepulse.shared.exception.OrganizationAccessException;

@ControllerAdvice
public class OrganizationAccessExceptionHandler {

    @ExceptionHandler(OrganizationAccessException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleOrganizationAccessException(
            OrganizationAccessException exception,
            Model model) {

        model.addAttribute(
                "accessMessage",
                exception.getMessage()
        );

        return "errors/organization-blocked";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaximumUploadSize(
            RedirectAttributes redirectAttributes) {

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "The schedule file is too large. Upload a CSV no larger than 5 MB."
        );

        return "redirect:/transportation-demand#upload";
    }
}
