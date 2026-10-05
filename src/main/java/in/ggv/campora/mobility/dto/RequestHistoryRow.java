package in.ggv.campora.mobility.dto;

import java.time.Instant;

import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;

public record RequestHistoryRow(String pickup, String destination, RequestType type, int seats,
                                RequestStatus status, Instant createdAt) {
}
