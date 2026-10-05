package in.ggv.campora.trip.dto;

/** A rider whose identity is revealed to the driver/host after acceptance. */
public record PassengerView(String name, String mobile, int seats) {
}
