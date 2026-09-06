package technology.renatus.routepulse.controller;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import technology.renatus.routepulse.dataTransferObject.DemoRequestForm;
import technology.renatus.routepulse.security.PublicRequestThrottle;
import technology.renatus.routepulse.service.DemoRequestService;

@Controller
public class LandingPageController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(LandingPageController.class);

    private static final String DEMO_FORM_ATTRIBUTE =
            "demoRequestForm";

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
            Model model,
            HttpServletRequest request) {

        /*
         * Spring Security 7 defers creation of the CSRF token until
         * something actually reads it. Because home.html is large,
         * the HTTP response can already be committed by the time the
         * demo form is rendered. Materialize the token here, before
         * Thymeleaf starts writing the response, so Spring never tries
         * to create the CSRF session too late.
         */
        ensureCsrfTokenReady(request);

        /*
         * A redirected validation failure may already have
         * placed the completed form back into the model.
         *
         * Only create a blank form when there is not already
         * one waiting for us.
         */
        if (!model.containsAttribute(DEMO_FORM_ATTRIBUTE)) {
            model.addAttribute(
                    DEMO_FORM_ATTRIBUTE,
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
            @ModelAttribute(DEMO_FORM_ATTRIBUTE)
            DemoRequestForm demoRequestForm,
            BindingResult bindingResult,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        /*
         * Protect the public form from repeated automated
         * submissions from the same connection.
         */
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

        /*
         * Do not render home.html directly from the POST.
         *
         * Instead, preserve the form and its validation errors
         * and redirect back to the landing page.
         *
         * This gives us a proper Post/Redirect/Get flow and
         * prevents form failures from interfering with the
         * landing-page response.
         */
        if (bindingResult.hasErrors()) {

            preserveFormForRedirect(
                    demoRequestForm,
                    bindingResult,
                    redirectAttributes
            );

            return "redirect:/#demo-request";
        }

        try {

            /*
             * DemoRequestService performs the actual delivery.
             */
            demoRequestService.submit(
                    demoRequestForm
            );

        } catch (Exception exception) {

            /*
             * A public contact form should never send a visitor
             * to the application's generic error page because
             * SMTP or another delivery component failed.
             *
             * Log the real exception for Railway/server
             * diagnostics and give the visitor a useful message.
             */
            LOGGER.error(
                    "RoutePulse demo request failed for email {} and organization {}.",
                    demoRequestForm.getEmail(),
                    demoRequestForm.getOrganizationName(),
                    exception
            );

            bindingResult.reject(
                    "demoRequest.failed",
                    "We could not send your request right now. Please email victor@renatus.technology."
            );

            preserveFormForRedirect(
                    demoRequestForm,
                    bindingResult,
                    redirectAttributes
            );

            return "redirect:/#demo-request";
        }

        /*
         * Use a flash attribute instead of a query-string flag.
         *
         * The message appears exactly once after the redirect.
         */
        redirectAttributes.addFlashAttribute(
                "demoRequestSent",
                true
        );

        return "redirect:/#demo-request";
    }

    private void preserveFormForRedirect(
            DemoRequestForm demoRequestForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        /*
         * Preserve what the visitor entered.
         */
        redirectAttributes.addFlashAttribute(
                DEMO_FORM_ATTRIBUTE,
                demoRequestForm
        );

        /*
         * Preserve Spring's validation result so Thymeleaf can
         * continue using #fields.hasErrors(...) and th:errors
         * after the redirect.
         */
        redirectAttributes.addFlashAttribute(
                BindingResult.MODEL_KEY_PREFIX
                        + DEMO_FORM_ATTRIBUTE,
                bindingResult
        );
    }


    private void ensureCsrfTokenReady(
            HttpServletRequest request) {

        Object csrfAttribute = request.getAttribute(
                CsrfToken.class.getName()
        );

        if (csrfAttribute instanceof CsrfToken csrfToken) {
            csrfToken.getToken();
        }
    }

    private boolean isSignedIn(
            Authentication authentication) {

        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication
                        instanceof AnonymousAuthenticationToken);
    }
}