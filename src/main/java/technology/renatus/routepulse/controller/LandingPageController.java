package technology.renatus.routepulse.controller;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.mail.MailException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import technology.renatus.routepulse.dataTransferObject.DemoRequestForm;
import technology.renatus.routepulse.security.PublicRequestThrottle;
import technology.renatus.routepulse.service.DemoRequestService;

@Controller
public class LandingPageController {

    private final DemoRequestService demoRequestService;
    private final PublicRequestThrottle throttle;

    public LandingPageController(
            DemoRequestService demoRequestService,
            PublicRequestThrottle throttle) {

        this.demoRequestService = demoRequestService;
        this.throttle = throttle;
    }

    @GetMapping("/")
    public String showLandingPage(
            Authentication authentication,
            Model model) {

        if (!model.containsAttribute("demoRequestForm")) {
            model.addAttribute(
                    "demoRequestForm",
                    new DemoRequestForm()
            );
        }

        model.addAttribute(
                "signedIn",
                isSignedIn(authentication)
        );

        return "home";
    }

    @PostMapping("/demo-request")
    public String submitDemoRequest(
            @Valid
            @ModelAttribute("demoRequestForm")
            DemoRequestForm demoRequestForm,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest request,
            Model model) {

        if (!throttle.tryAcquire(
                "demo-request",
                request.getRemoteAddr(),
                5,
                Duration.ofHours(1)
        )) {
            bindingResult.reject(
                    "demoRequest.rateLimited",
                    "Too many demo requests were received from this connection. Try again later."
            );
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "signedIn",
                    isSignedIn(authentication)
            );
            return "home";
        }

        try {
            demoRequestService.submit(demoRequestForm);
        } catch (MailException | IllegalStateException exception) {
            bindingResult.reject(
                    "demoRequest.failed",
                    "We could not send your request right now. Please email victor@renatus.technology."
            );
            model.addAttribute(
                    "signedIn",
                    isSignedIn(authentication)
            );
            return "home";
        }

        return "redirect:/?demoRequestSent#demo-request";
    }

    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

