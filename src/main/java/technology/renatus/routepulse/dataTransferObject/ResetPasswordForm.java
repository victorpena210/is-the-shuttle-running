package technology.renatus.routepulse.dataTransferObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordForm {

    @NotBlank(message = "The password-reset token is required.")
    private String token;

    @NotBlank(message = "Password is required.")
    @Size(
            min = 12,
            max = 72,
            message = "Password must contain between 12 and 72 characters."
    )
    private String password;

    @NotBlank(message = "Please confirm your password.")
    @Size(
            min = 12,
            max = 72,
            message = "Password confirmation must contain between 12 and 72 characters."
    )
    private String confirmPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
