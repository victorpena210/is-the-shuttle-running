package technology.renatus.routepulse.driver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import technology.renatus.routepulse.auth.model.UserAccount;
import technology.renatus.routepulse.organization.model.Organization;

@Entity
@Table(
        name = "drivers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_drivers_organization_employee_id",
                        columnNames = {
                                "organization_id",
                                "employee_id"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_drivers_organization_email",
                        columnNames = {
                                "organization_id",
                                "email"
                        }
                )
        }
)
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "organization_id",
            nullable = false,
            updatable = false
    )
    private Organization organization;

    @Column(
            name = "employee_id",
            nullable = false,
            length = 50
    )
    private String employeeId;

    @Column(
            nullable = false,
            length = 200
    )
    private String name;

    @Column(
            length = 254
    )
    private String email;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            unique = true
    )
    private UserAccount user;

    @Column(nullable = false)
    private boolean active;

    protected Driver() {
    }

    /*
     * ORIGINAL CONSTRUCTOR
     *
     * Keep this constructor for existing tests
     * and older parts of RoutePulse that create
     * drivers without an account/email.
     */
    public Driver(
            Organization organization,
            String employeeId,
            String name,
            boolean active) {

        this(
                organization,
                employeeId,
                name,
                null,
                active
        );
    }

    /*
     * NEW DRIVER-ACCOUNT CONSTRUCTOR
     *
     * Used when a manager registers a driver
     * with an email address and sends an invitation.
     */
    public Driver(
            Organization organization,
            String employeeId,
            String name,
            String email,
            boolean active) {

        this.organization = organization;
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void changeEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void changeEmail(String email) {

        if (hasAccount()) {
            throw new IllegalStateException(
                    "The driver's email cannot be changed here because their Is the Shuttle Running account is already active."
            );
        }

        this.email = email;
    }

    public UserAccount getUser() {
        return user;
    }

    public boolean hasAccount() {
        return user != null;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void linkUser(UserAccount user) {

        if (user == null) {
            throw new IllegalArgumentException(
                    "Driver account is required."
            );
        }

        if (this.user != null
                && !this.user.getId()
                        .equals(user.getId())) {

            throw new IllegalStateException(
                    "This driver is already linked to another account."
            );
        }

        this.user = user;
    }
}
