package technology.renatus.routepulse.dataTransferObject;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResendVerificationForm {

    @NotBlank(message = "Email address is required.")
    @Email(message = "Enter a valid email address.")
    @Size(
            max = 254,
            message = "Email address cannot exceed 254 characters."
    )
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
