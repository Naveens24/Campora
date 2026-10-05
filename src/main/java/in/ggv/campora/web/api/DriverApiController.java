package in.ggv.campora.web.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.mobility.dto.HotspotView;
import in.ggv.campora.mobility.service.MatchingService;
import in.ggv.campora.profile.dto.DriverStatusView;
import in.ggv.campora.profile.service.ProfileService;
import in.ggv.campora.trip.service.TripService;

/**
 * JSON endpoints for drivers. The dashboard polls {@code /hotspots}; the same
 * contract is ready for a future Flutter app.
 */
@RestController
@RequestMapping("/api/v1/driver")
public class DriverApiController {

    public record HotspotBoard(Instant generatedAt, int capacity, boolean canReserve, List<HotspotView> hotspots) {
    }

    private final MatchingService matching;
    private final ProfileService profiles;
    private final TripService trips;

    public DriverApiController(MatchingService matching, ProfileService profiles, TripService trips) {
        this.matching = matching;
        this.profiles = profiles;
        this.trips = trips;
    }

    @GetMapping("/hotspots")
    public ResponseEntity<HotspotBoard> hotspots(@AuthenticationPrincipal CamporaUserDetails me) {
        DriverStatusView status = profiles.driverStatus(me.userId());
        boolean canReserve = status.verified() && status.online() && trips.activeTrip(me.userId()).isEmpty();
        List<HotspotView> hotspots = status.verified() ? matching.hotspots() : List.of();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new HotspotBoard(Instant.now(), status.capacity(), canReserve, hotspots));
    }
}
