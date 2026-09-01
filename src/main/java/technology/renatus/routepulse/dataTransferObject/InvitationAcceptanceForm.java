package technology.renatus.routepulse.dataTransferObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InvitationAcceptanceForm {

    @NotBlank
    private String token;

    @NotBlank(
            message = "First name is required."
    )
    @Size(
            max = 100,
            message = "First name cannot exceed 100 characters."
    )
    private String firstName;

    @NotBlank(
            message = "Last name is required."
    )
    @Size(
            max = 100,
            message = "Last name cannot exceed 100 characters."
    )
    private String lastName;

    @NotBlank(
            message = "Password is required."
    )
    @Size(
            min = 12,
            max = 72,
            message = "Password must contain between 12 and 72 characters."
    )
    private String password;

    @NotBlank(
            message = "Please confirm your password."
    )
    private String confirmPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(
            String firstName) {

        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(
            String lastName) {

        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(
            String password) {

        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(
            String confirmPassword) {

        this.confirmPassword =
                confirmPassword;
    }
}