package in.ggv.campora.web.form;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class StudentProfileForm {

    @NotNull(message = "Choose your department")
    private UUID departmentId;

    @NotBlank(message = "Enrollment number is required")
    @Pattern(regexp = "^[A-Za-z0-9/\\-]{4,32}$", message = "Letters, digits, / and - only (4–32 characters)")
    private String enrollmentNo;

    @NotNull(message = "Semester is required")
    @Min(value = 1, message = "Semester must be 1–12")
    @Max(value = 12, message = "Semester must be 1–12")
    private Integer semester;

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public String getEnrollmentNo() {
        return enrollmentNo;
    }

    public void setEnrollmentNo(String enrollmentNo) {
        this.enrollmentNo = enrollmentNo;
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }
}
