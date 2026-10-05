package in.ggv.campora.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.mobility.dto.HotspotView;
import in.ggv.campora.mobility.dto.ReservationResult;
import in.ggv.campora.mobility.service.MatchingService;
import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.dto.DriverStatusView;
import in.ggv.campora.profile.service.ProfileService;
import in.ggv.campora.trip.dto.DriverTripView;
import in.ggv.campora.trip.service.TripService;

/** Driver console: availability, live hotspot board, current trip. */
@Controller
@RequestMapping("/driver")
public class DriverController {

    private final ProfileService profiles;
    private final MatchingService matching;
    private final TripService trips;
    private final CamporaProperties properties;

    public DriverController(ProfileService profiles, MatchingService matching, TripService trips,
                            CamporaProperties properties) {
        this.profiles = profiles;
        this.matching = matching;
        this.trips = trips;
        this.properties = properties;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        DriverStatusView status = profiles.driverStatus(me.userId());
        DriverTripView trip = trips.activeTrip(me.userId()).orElse(null);
        boolean canReserve = status.verified() && status.online() && trip == null;
        List<HotspotView> hotspots = status.verified() ? matching.hotspots() : List.of();

        model.addAttribute("status", status);
        model.addAttribute("trip", trip);
        model.addAttribute("canReserve", canReserve);
        model.addAttribute("hotspots", hotspots);
        model.addAttribute("refreshSeconds", properties.mobility().hotspotRefreshSeconds());
        return "driver/dashboard";
    }

    @PostMapping("/availability")
    public String availability(@AuthenticationPrincipal CamporaUserDetails me,
                               @RequestParam("online") boolean online, RedirectAttributes attrs) {
        profiles.setAvailability(me.userId(), online ? Availability.ONLINE : Availability.OFFLINE);
        Flash.success(attrs, online ? "You're online — live demand is below." : "You're offline.");
        return "redirect:/driver";
    }

    @PostMapping("/reserve")
    public String reserve(@AuthenticationPrincipal CamporaUserDetails me,
                          @RequestParam("pickupId") UUID pickupId,
                          @RequestParam("destinationId") UUID destinationId,
                          @RequestParam("seats") int seats, RedirectAttributes attrs) {
        ReservationResult result = matching.reserveSeats(me.userId(), pickupId, destinationId, seats);
        String tail = result.hotspotClosed()
                ? " The hotspot is now closed."
                : " " + result.seatsStillWaiting() + " seat(s) of demand still waiting for another driver.";
        Flash.success(attrs, "Reserved " + result.seatsFilled() + " seat(s) for "
                + result.requestsMatched() + " request(s)." + tail);
        return "redirect:/driver";
    }

    @PostMapping("/trips/{id}/start")
    public String start(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                        RedirectAttributes attrs) {
        trips.start(me.userId(), id);
        Flash.success(attrs, "Trip started. Drive safe!");
        return "redirect:/driver";
    }

    @PostMapping("/trips/{id}/complete")
    public String complete(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                           RedirectAttributes attrs) {
        trips.complete(me.userId(), id);
        Flash.success(attrs, "Trip completed and recorded.");
        return "redirect:/driver";
    }

    @PostMapping("/trips/{id}/cancel")
    public String cancel(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                         RedirectAttributes attrs) {
        trips.cancel(me.userId(), id);
        Flash.success(attrs, "Trip cancelled. The students are back on the board.");
        return "redirect:/driver";
    }

    @GetMapping("/history")
    public String history(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        model.addAttribute("trips", trips.history(me.userId(), 50));
        return "driver/history";
    }
}
