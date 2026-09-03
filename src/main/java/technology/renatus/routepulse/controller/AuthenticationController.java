package technology.renatus.routepulse.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class AuthenticationController {

	private final boolean registrationEnabled;

	public AuthenticationController(
			@Value("${app.registration.enabled:true}")
			boolean registrationEnabled) {

		this.registrationEnabled = registrationEnabled;
	}
	
    @GetMapping("/login")
    public String showLoginPage(Model model) {

		model.addAttribute(
				"registrationEnabled",
				registrationEnabled
		);

        return "login";
    }

}
