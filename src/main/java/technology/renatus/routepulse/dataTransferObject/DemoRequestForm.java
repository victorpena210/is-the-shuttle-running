package technology.renatus.routepulse.dataTransferObject;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DemoRequestForm {

    @NotBlank(message = "Your name is required.")
    @Size(max = 150, message = "Name cannot exceed 150 characters.")
    private String name;

    @NotBlank(message = "Work email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254, message = "Email address cannot exceed 254 characters.")
    private String email;

    @NotBlank(message = "Organization name is required.")
    @Size(max = 200, message = "Organization name cannot exceed 200 characters.")
    private String organizationName;

    @Size(max = 150, message = "Role cannot exceed 150 characters.")
    private String role;

    @Size(max = 1200, message = "Message cannot exceed 1200 characters.")
    private String message;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
