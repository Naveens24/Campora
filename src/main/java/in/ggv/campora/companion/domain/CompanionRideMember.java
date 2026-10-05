package in.ggv.campora.companion.domain;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.profile.domain.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A student riding in someone's companion ride. */
@Entity
@Table(name = "companion_ride_member")
@SQLRestriction("deleted = false")
public class CompanionRideMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ride_id", nullable = false)
    private CompanionRide ride;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    /** The student's open request this seat fulfils, if they had one on the same route. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_request_id")
    private TravelRequest travelRequest;

    @Column(name = "seats", nullable = false)
    private int seats;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MemberStatus status = MemberStatus.JOINED;

    protected CompanionRideMember() {
    }

    public CompanionRideMember(CompanionRide ride, StudentProfile student, int seats, TravelRequest travelRequest) {
        this.ride = ride;
        this.student = student;
        this.seats = seats;
        this.travelRequest = travelRequest;
    }

    public void leave() {
        this.status = MemberStatus.LEFT;
    }

    public CompanionRide getRide() {
        return ride;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public TravelRequest getTravelRequest() {
        return travelRequest;
    }

    public int getSeats() {
        return seats;
    }

    public MemberStatus getStatus() {
        return status;
    }
}
