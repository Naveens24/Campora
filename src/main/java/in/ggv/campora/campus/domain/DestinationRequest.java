package in.ggv.campora.campus.domain;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

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

/** A student's proposal to add a stop that is missing from the network. */
@Entity
@Table(name = "destination_request")
@SQLRestriction("deleted = false")
public class DestinationRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(name = "proposed_name", nullable = false, length = 120)
    private String proposedName;

    @Column(name = "area", length = 120)
    private String area;

    @Column(name = "reason", length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DestinationRequestStatus status = DestinationRequestStatus.PENDING;

    @Column(name = "review_note", length = 300)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected DestinationRequest() {
    }

    public DestinationRequest(StudentProfile student, String proposedName, String area, String reason) {
        this.student = student;
        this.proposedName = proposedName;
        this.area = area;
        this.reason = reason;
    }

    public void approve(String note, Instant at) {
        review(DestinationRequestStatus.APPROVED, note, at);
    }

    public void reject(String note, Instant at) {
        review(DestinationRequestStatus.REJECTED, note, at);
    }

    private void review(DestinationRequestStatus outcome, String note, Instant at) {
        if (status != DestinationRequestStatus.PENDING) {
            throw new BusinessException("This request has already been reviewed.");
        }
        this.status = outcome;
        this.reviewNote = note;
        this.reviewedAt = at;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public String getProposedName() {
        return proposedName;
    }

    public String getArea() {
        return area;
    }

    public String getReason() {
        return reason;
    }

    public DestinationRequestStatus getStatus() {
        return status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
