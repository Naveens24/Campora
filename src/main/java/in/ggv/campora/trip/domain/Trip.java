package in.ggv.campora.trip.domain;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.companion.domain.CompanionRide;
import in.ggv.campora.profile.domain.DriverProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * A physical journey. An AUTO trip groups every driver response from one
 * seat reservation; a COMPANION trip is created when a host starts their ride.
 */
@Entity
@Table(name = "trip")
@SQLRestriction("deleted = false")
public class Trip extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false, length = 16)
    private TripType tripType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private DriverProfile driver;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "companion_ride_id", unique = true)
    private CompanionRide companionRide;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id", nullable = false)
    private Destination pickup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "seats_reserved", nullable = false)
    private int seatsReserved;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TripStatus status = TripStatus.SCHEDULED;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    protected Trip() {
    }

    public static Trip auto(DriverProfile driver, Destination pickup, Destination destination, int seats) {
        Trip trip = new Trip();
        trip.tripType = TripType.AUTO;
        trip.driver = driver;
        trip.pickup = pickup;
        trip.destination = destination;
        trip.seatsReserved = seats;
        return trip;
    }

    public static Trip companion(CompanionRide ride, int seats) {
        Trip trip = new Trip();
        trip.tripType = TripType.COMPANION;
        trip.companionRide = ride;
        trip.pickup = ride.getPickup();
        trip.destination = ride.getDestination();
        trip.seatsReserved = seats;
        return trip;
    }

    public void start(Instant now) {
        if (status != TripStatus.SCHEDULED) {
            throw new BusinessException("Only a scheduled trip can be started.");
        }
        this.status = TripStatus.IN_PROGRESS;
        this.startTime = now;
    }

    public void complete(Instant now) {
        if (status != TripStatus.IN_PROGRESS) {
            throw new BusinessException("Only a trip in progress can be completed.");
        }
        this.status = TripStatus.COMPLETED;
        this.endTime = now;
    }

    public void cancel(Instant now) {
        if (status != TripStatus.SCHEDULED) {
            throw new BusinessException("A trip can only be cancelled before it starts.");
        }
        this.status = TripStatus.CANCELLED;
        this.endTime = now;
    }

    /** A passenger dropped out before departure. */
    public void releaseSeats(int seats) {
        this.seatsReserved = Math.max(0, this.seatsReserved - seats);
    }

    public boolean isActive() {
        return TripStatus.ACTIVE.contains(status);
    }

    public TripType getTripType() {
        return tripType;
    }

    public DriverProfile getDriver() {
        return driver;
    }

    public CompanionRide getCompanionRide() {
        return companionRide;
    }

    public Destination getPickup() {
        return pickup;
    }

    public Destination getDestination() {
        return destination;
    }

    public int getSeatsReserved() {
        return seatsReserved;
    }

    public TripStatus getStatus() {
        return status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }
}
