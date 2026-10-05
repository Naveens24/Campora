package in.ggv.campora.web.form;

import in.ggv.campora.campus.domain.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LocationForm {

    @NotBlank(message = "Stop name is required")
    @Size(max = 120)
    private String name;

    @Size(max = 120)
    private String area;

    @NotNull
    private LocationType type = LocationType.CITY;

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

    public LocationType getType() {
        return type;
    }

    public void setType(LocationType type) {
        this.type = type;
    }
}
