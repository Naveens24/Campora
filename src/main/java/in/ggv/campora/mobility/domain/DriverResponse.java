package in.ggv.campora.mobility.domain;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.trip.domain.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A driver's claim on one travel request. A request may collect many
 * responses over its life (e.g. a driver cancels and another accepts),
 * but only one may be ACCEPTED at a time — enforced under row locks in
 * MatchingService and by a partial unique index on PostgreSQL.
 *
 * <p>All responses a driver creates in one reservation share the same
 * {@link Trip}, which is how ten separate requests become one ride.</p>
 */
@Entity
@Table(name = "driver_response")
@SQLRestriction("deleted = false")
public class DriverResponse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private TravelRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private DriverProfile driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "seats_reserved", nullable = false)
    private int seatsReserved;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ResponseStatus status = ResponseStatus.ACCEPTED;

    protected DriverResponse() {
    }

    public DriverResponse(TravelRequest request, DriverProfile driver, Trip trip) {
        this.request = request;
        this.driver = driver;
        this.trip = trip;
        this.seatsReserved = request.getSeatsRequired();
    }

    public void cancel() {
        this.status = ResponseStatus.CANCELLED;
    }

    public boolean isAccepted() {
        return status == ResponseStatus.ACCEPTED;
    }

    public TravelRequest getRequest() {
        return request;
    }

    public DriverProfile getDriver() {
        return driver;
    }

    public Trip getTrip() {
        return trip;
    }

    public int getSeatsReserved() {
        return seatsReserved;
    }

    public ResponseStatus getStatus() {
        return status;
    }
}
