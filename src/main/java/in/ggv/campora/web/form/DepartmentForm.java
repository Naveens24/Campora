package in.ggv.campora.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DepartmentForm {

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[A-Za-z]{2,16}$", message = "2–16 letters")
    private String code;

    @NotBlank(message = "Name is required")
    @Size(max = 120)
    private String name;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
