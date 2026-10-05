package in.ggv.campora.mobility.domain;

import java.time.Duration;
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

/**
 * One student's demand for a route (internally also called MobilityRequest).
 *
 * <p>All state changes go through the methods below, so an illegal
 * transition (e.g. completing a request that was never matched) fails fast
 * instead of silently corrupting data.</p>
 */
@Entity
@Table(name = "travel_request")
@SQLRestriction("deleted = false")
public class TravelRequest extends BaseEntity {

    /** A request put back into the pool gets at least this long to find a new ride. */
    public static final Duration REOPEN_GRACE = Duration.ofMinutes(10);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_id", nullable = false)
    private Destination pickup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Destination destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 16)
    private RequestType requestType;

    @Column(name = "seats_required", nullable = false)
    private int seatsRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RequestStatus status = RequestStatus.OPEN;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected TravelRequest() {
    }

    public TravelRequest(StudentProfile student, Destination pickup, Destination destination,
                         RequestType requestType, int seatsRequired, Instant expiresAt) {
        this.student = student;
        this.pickup = pickup;
        this.destination = destination;
        this.requestType = requestType;
        this.seatsRequired = seatsRequired;
        this.expiresAt = expiresAt;
    }

    // ── state transitions ──────────────────────────────────────

    public void markMatched() {
        require(RequestStatus.OPEN, "matched");
        this.status = RequestStatus.MATCHED;
    }

    public void markInTrip() {
        require(RequestStatus.MATCHED, "started");
        this.status = RequestStatus.IN_TRIP;
    }

    public void markCompleted() {
        require(RequestStatus.IN_TRIP, "completed");
        this.status = RequestStatus.COMPLETED;
    }

    /** Put a matched request back into the pool (driver cancelled, companion left). */
    public void reopen(Instant minimumExpiry) {
        require(RequestStatus.MATCHED, "reopened");
        this.status = RequestStatus.OPEN;
        if (expiresAt.isBefore(minimumExpiry)) {
            this.expiresAt = minimumExpiry;
        }
    }

    public void cancel() {
        if (status != RequestStatus.OPEN && status != RequestStatus.MATCHED) {
            throw new BusinessException("Only open or matched requests can be cancelled.");
        }
        this.status = RequestStatus.CANCELLED;
    }

    private void require(RequestStatus expected, String action) {
        if (status != expected) {
            throw new BusinessException("Request cannot be " + action + " while it is " + status + ".");
        }
    }

    public boolean isActive() {
        return status.isActive();
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Destination getPickup() {
        return pickup;
    }

    public Destination getDestination() {
        return destination;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public int getSeatsRequired() {
        return seatsRequired;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
