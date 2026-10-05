package in.ggv.campora.campus.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.campus.domain.DestinationRequest;
import in.ggv.campora.campus.domain.DestinationRequestStatus;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.dto.DestinationRequestRow;
import in.ggv.campora.campus.repository.DestinationRequestRepository;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.profile.domain.StudentProfile;
import in.ggv.campora.profile.service.ProfileService;

/** Students propose missing stops; the transport office approves or rejects them. */
@Service
public class DestinationRequestService {

    private final DestinationRequestRepository proposals;
    private final CampusService campus;
    private final ProfileService profiles;
    private final Clock clock;

    public DestinationRequestService(DestinationRequestRepository proposals, CampusService campus,
                                     ProfileService profiles, Clock clock) {
        this.proposals = proposals;
        this.campus = campus;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public void submit(UUID userId, String name, String area, String reason) {
        StudentProfile student = profiles.requireStudent(userId);
        if (proposals.existsByStudentIdAndStatus(student.getId(), DestinationRequestStatus.PENDING)) {
            throw new BusinessException("You already have a suggestion waiting for review.");
        }
        if (campus.locationExists(name)) {
            throw new BusinessException("\"" + name.trim() + "\" is already a stop — pick it from the list.");
        }
        proposals.save(new DestinationRequest(student, name.trim(), trimToNull(area), trimToNull(reason)));
    }

    @Transactional(readOnly = true)
    public List<DestinationRequestRow> mine(UUID userId) {
        StudentProfile student = profiles.requireStudent(userId);
        return proposals.findTop10ByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(r -> toRow(r, "You"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DestinationRequestRow> pending() {
        return proposals.findByStatusWithStudent(DestinationRequestStatus.PENDING).stream()
                .map(r -> toRow(r, r.getStudent().getUser().getFullName()))
                .toList();
    }

    /** Approving adds the stop to the network immediately. */
    @Transactional
    public void approve(UUID proposalId, LocationType type) {
        DestinationRequest proposal = require(proposalId);
        String note;
        if (campus.locationExists(proposal.getProposedName())) {
            note = "Already available as a stop.";
        } else {
            campus.addLocation(proposal.getProposedName(), proposal.getArea(),
                    type == null ? LocationType.CITY : type);
            note = "Added to the network.";
        }
        proposal.approve(note, clock.instant());
    }

    @Transactional
    public void reject(UUID proposalId, String note) {
        require(proposalId).reject(trimToNull(note), clock.instant());
    }

    private DestinationRequest require(UUID id) {
        return proposals.findById(id).orElseThrow(() -> new NotFoundException("Suggestion"));
    }

    private static DestinationRequestRow toRow(DestinationRequest r, String studentName) {
        return new DestinationRequestRow(r.getId(), studentName, r.getProposedName(), r.getArea(), r.getReason(),
                r.getStatus(), r.getReviewNote(), r.getCreatedAt());
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
