package technology.renatus.routepulse.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import technology.renatus.routepulse.exception.OrganizationAccessException;

@ControllerAdvice
public class OrganizationAccessExceptionHandler {

    @ExceptionHandler(OrganizationAccessException.class)
    public String handleOrganizationAccessException(
            OrganizationAccessException exception,
            Model model) {

        model.addAttribute(
                "accessMessage",
                exception.getMessage()
        );

        return "organization-blocked";
    }
}