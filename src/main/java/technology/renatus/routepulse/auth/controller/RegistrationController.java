package technology.renatus.routepulse.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Self-service organization registration has been permanently removed. */
@Controller
public class RegistrationController {
    @RequestMapping({"/register", "/organization/register"})
    public ResponseEntity<Void> registrationRemoved() {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore()).build();
    }
}
