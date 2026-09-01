package technology.renatus.routepulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
            length = 100
    )
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected Driver() {
    }

    public Driver(
            Organization organization,
            String employeeId,
            String name,
            boolean active) {

        this.organization = organization;
        this.employeeId = employeeId;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}