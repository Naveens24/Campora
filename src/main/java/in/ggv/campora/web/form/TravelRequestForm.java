package in.ggv.campora.web.form;

import java.util.UUID;

import in.ggv.campora.mobility.domain.RequestType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class TravelRequestForm {

    @NotNull(message = "Choose a pickup point")
    private UUID pickupId;

    @NotNull(message = "Choose a destination")
    private UUID destinationId;

    @NotNull
    private RequestType requestType = RequestType.ANY;

    @NotNull
    @Min(1)
    @Max(6)
    private Integer seatsRequired = 1;

    public UUID getPickupId() {
        return pickupId;
    }

    public void setPickupId(UUID pickupId) {
        this.pickupId = pickupId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public Integer getSeatsRequired() {
        return seatsRequired;
    }

    public void setSeatsRequired(Integer seatsRequired) {
        this.seatsRequired = seatsRequired;
    }
}
