package in.ggv.campora.admin.dto;

public record AdminStats(
        long students,
        long drivers,
        long driversOnline,
        long openRequests,
        long activeTrips,
        long tripsCompletedToday,
        long activeCompanionRides,
        long pendingSuggestions,
        long unverified,
        long suspended) {
}
