package in.ggv.campora.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DestinationProposalForm {

    @NotBlank(message = "Name the place")
    @Size(min = 3, max = 120)
    private String name;

    @Size(max = 120)
    private String area;

    @Size(max = 300)
    private String reason;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
