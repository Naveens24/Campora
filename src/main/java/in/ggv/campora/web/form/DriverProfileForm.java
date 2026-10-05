package in.ggv.campora.web.form;

import in.ggv.campora.profile.domain.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DriverProfileForm {

    @NotNull(message = "Choose your vehicle type")
    private VehicleType vehicleType = VehicleType.AUTO;

    @NotBlank(message = "Vehicle number is required")
    @Pattern(regexp = "^[A-Za-z]{2}\\s?\\d{1,2}\\s?[A-Za-z]{0,3}\\s?\\d{1,4}$",
             message = "Use the registration format, e.g. CG10 AB 1234")
    private String vehicleNumber;

    @NotNull(message = "Seat capacity is required")
    @Min(value = 1, message = "At least 1 seat")
    @Max(value = 12, message = "At most 12 seats")
    private Integer seatCapacity = 5;

    @Size(max = 120)
    private String nativeArea;

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public Integer getSeatCapacity() {
        return seatCapacity;
    }

    public void setSeatCapacity(Integer seatCapacity) {
        this.seatCapacity = seatCapacity;
    }

    public String getNativeArea() {
        return nativeArea;
    }

    public void setNativeArea(String nativeArea) {
        this.nativeArea = nativeArea;
    }
}
