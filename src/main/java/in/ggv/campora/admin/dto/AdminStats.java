package in.ggv.campora.admin.dto;

public record AdminStats(
        long students,
        long drivers,
        long driversAwaitingVerification,
        long driversOnline,
        long openRequests,
        long activeTrips,
        long tripsCompletedToday,
        long activeCompanionRides,
        long pendingSuggestions) {
}
