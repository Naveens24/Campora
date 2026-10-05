package in.ggv.campora.campus.dto;

import java.time.Instant;
import java.util.UUID;

import in.ggv.campora.campus.domain.DestinationRequestStatus;

public record DestinationRequestRow(UUID id, String studentName, String proposedName, String area,
                                    String reason, DestinationRequestStatus status, String reviewNote,
                                    Instant createdAt) {
}
