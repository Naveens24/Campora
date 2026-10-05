package in.ggv.campora.companion.domain;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.campus.domain.Destination;
import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.profile.domain.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A ride offered by a student (the "companion") to fellow students. */
@Entity
@Table(name = "companion_ride")
@SQLRestriction("deleted = false")
public class CompanionRide extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private StudentProfile host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id", nullable = false)
    private Destination pickup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RideStatus status = RideStatus.OPEN;

    @Column(name = "note", length = 200)
    private String note;

    protected CompanionRide() {
    }

    public CompanionRide(StudentProfile host, Destination pickup, Destination destination,
                         Instant departureTime, int totalSeats, String note) {
        this.host = host;
        this.pickup = pickup;
        this.destination = destination;
        this.departureTime = departureTime;
        this.totalSeats = totalSeats;
        this.availableSeats = totalSeats;
        this.note = note;
    }

    public void takeSeats(int seats) {
        if (status != RideStatus.OPEN) {
            throw new BusinessException("This ride is no longer taking passengers.");
        }
        if (seats > availableSeats) {
            throw new BusinessException("Only " + availableSeats + " seat(s) left on this ride.");
        }
        availableSeats -= seats;
        if (availableSeats == 0) {
            status = RideStatus.FULL;
        }
    }

    public void returnSeats(int seats) {
        availableSeats = Math.min(totalSeats, availableSeats + seats);
        if (status == RideStatus.FULL && availableSeats > 0) {
            status = RideStatus.OPEN;
        }
    }

    public void start() {
        if (!RideStatus.JOINABLE_OR_FULL.contains(status)) {
            throw new BusinessException("This ride cannot be started now.");
        }
        if (bookedSeats() == 0) {
            throw new BusinessException("No one has joined yet — cancel the ride instead.");
        }
        status = RideStatus.STARTED;
    }

    public void complete() {
        if (status != RideStatus.STARTED) {
            throw new BusinessException("Only a started ride can be completed.");
        }
        status = RideStatus.COMPLETED;
    }

    public void cancel() {
        if (!RideStatus.JOINABLE_OR_FULL.contains(status)) {
            throw new BusinessException("A ride can only be cancelled before it starts.");
        }
        status = RideStatus.CANCELLED;
    }

    public int bookedSeats() {
        return totalSeats - availableSeats;
    }

    public boolean isHostedBy(StudentProfile student) {
        return host.getId().equals(student.getId());
    }

    public StudentProfile getHost() {
        return host;
    }

    public Destination getPickup() {
        return pickup;
    }

    public Destination getDestination() {
        return destination;
    }

    public Instant getDepartureTime() {
        return departureTime;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public RideStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }
}
