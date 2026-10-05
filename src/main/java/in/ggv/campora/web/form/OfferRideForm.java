package in.ggv.campora.web.form;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class OfferRideForm {

    @NotNull(message = "Choose a pickup point")
    private UUID pickupId;

    @NotNull(message = "Choose a destination")
    private UUID destinationId;

    @NotNull(message = "Choose a departure time")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime departureTime;

    @NotNull
    @Min(1)
    @Max(12)
    private Integer totalSeats = 2;

    @Size(max = 200)
    private String note;

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

    public LocalDateTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalDateTime departureTime) {
        this.departureTime = departureTime;
    }

    public Integer getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(Integer totalSeats) {
        this.totalSeats = totalSeats;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
