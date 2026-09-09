package technology.renatus.routepulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Organization legal name is required.")
    @Size(
            max = 200,
            message = "Organization legal name cannot exceed 200 characters."
    )
    private String organizationLegalName;

    @NotBlank(message = "Organization display name is required.")
    @Size(
            max = 150,
            message = "Organization display name cannot exceed 150 characters."
    )
    private String organizationDisplayName;

    @NotBlank(message = "First name is required.")
    @Size(
            max = 100,
            message = "First name cannot exceed 100 characters."
    )
    private String firstName;

    @NotBlank(message = "Last name is required.")
    @Size(
            max = 100,
            message = "Last name cannot exceed 100 characters."
    )
    private String lastName;

    @NotBlank(message = "Email address is required.")
    @Email(message = "Enter a valid email address.")
    @Size(
            max = 254,
            message = "Email address cannot exceed 254 characters."
    )
    private String email;

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

    public String getOrganizationLegalName() {
        return organizationLegalName;
    }

    public void setOrganizationLegalName(
            String organizationLegalName) {

        this.organizationLegalName =
                organizationLegalName;
    }

    public String getOrganizationDisplayName() {
        return organizationDisplayName;
    }

    public void setOrganizationDisplayName(
            String organizationDisplayName) {

        this.organizationDisplayName =
                organizationDisplayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public void setConfirmPassword(
            String confirmPassword) {

        this.confirmPassword = confirmPassword;
    }
}
