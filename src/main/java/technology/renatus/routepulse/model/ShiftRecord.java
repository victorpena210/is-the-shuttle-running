package technology.renatus.routepulse.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class ShiftRecord {

    private String employeeId;
    private String employeeName;
    private String department;
    private String job;
    private LocalDate shiftDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String pickupLocation;

    public ShiftRecord() {
    }

    public ShiftRecord(String employeeId, String employeeName, String department, String job,
                       LocalDate shiftDate, LocalTime startTime, LocalTime endTime, String pickupLocation) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.job = job;
        this.shiftDate = shiftDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.pickupLocation = pickupLocation;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartment() {
        return department;
    }

    public String getJob() {
        return job;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }
}