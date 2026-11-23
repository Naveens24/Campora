package in.ggv.campora.web.form;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class StudentProfileForm {

    @NotNull(message = "Choose your department")
    private UUID departmentId;

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

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }
}
