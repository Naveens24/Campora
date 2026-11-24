package in.ggv.campora.web;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.campus.dto.LocationOption;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.companion.dto.PublicRide;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.mobility.service.TravelRequestService;
import in.ggv.campora.web.RideIntent.Kind;
import in.ggv.campora.web.form.RouteForm;
import jakarta.servlet.http.HttpSession;

/** Public ride search. Looking needs no account; joining, requesting or offering a ride does. */
@Controller
@RequestMapping("/rides")
public class RidesController {

    private static final int OTHER_RIDES = 6;

    private final CompanionService companions;
    private final TravelRequestService requests;
    private final CampusService campus;

    public RidesController(CompanionService companions, TravelRequestService requests, CampusService campus) {
        this.companions = companions;
        this.requests = requests;
        this.campus = campus;
    }

    @GetMapping
    public String search(@ModelAttribute("route") RouteForm route, BindingResult ignored, Model model) {
        List<PublicRide> rides = companions.upcomingRides();
        String problem = problemWith(route);
        boolean searched = problem == null && route.complete();
        if (problem != null) {
            model.addAttribute(Flash.ERROR, problem);
        }
        if (searched) {
            UUID from = route.getFrom();
            UUID to = route.getTo();
            model.addAttribute("matches", rides.stream().filter(r -> r.onRoute(from, to)).toList());
            model.addAttribute("others", rides.stream().filter(r -> !r.onRoute(from, to))
                    .sorted(Comparator.comparingInt(r -> r.distanceFrom(from, to))).limit(OTHER_RIDES).toList());
            model.addAttribute("waiting", requests.waitingOnRoute(from, to));
        } else {
            model.addAttribute("others", rides.stream().limit(OTHER_RIDES).toList());
        }
        model.addAttribute("searched", searched);
        model.addAttribute("locations", campus.activeLocations());
        return "rides";
    }

    // ── from here on an account is needed ──────────────────────

    @GetMapping("/{id}")
    public String join(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                       HttpSession session, RedirectAttributes attrs) {
        Optional<PublicRide> ride = companions.upcomingRides().stream().filter(r -> r.id().equals(id)).findFirst();
        if (ride.isEmpty()) {
            return back(attrs, "That ride has left or filled up.");
        }
        PublicRide r = ride.get();
        return proceed(me, new RideIntent(Kind.JOIN, r.pickupId(), r.destinationId(), id, r.pickup(), r.destination()),
                session, attrs);
    }

    @GetMapping("/request")
    public String request(@AuthenticationPrincipal CamporaUserDetails me, @ModelAttribute("route") RouteForm route,
                          BindingResult ignored, HttpSession session, RedirectAttributes attrs) {
        String problem = route.complete() ? problemWith(route) : "Choose a pickup and a drop.";
        return problem != null ? back(attrs, problem) : proceed(me, intent(Kind.REQUEST, route), session, attrs);
    }

    @GetMapping("/offer")
    public String offer(@AuthenticationPrincipal CamporaUserDetails me, @ModelAttribute("route") RouteForm route,
                        BindingResult ignored, HttpSession session, RedirectAttributes attrs) {
        String problem = problemWith(route);
        return problem != null ? back(attrs, problem) : proceed(me, intent(Kind.OFFER, route), session, attrs);
    }

    private String proceed(CamporaUserDetails me, RideIntent intent, HttpSession session, RedirectAttributes attrs) {
        if (me != null && me.ready() && !me.passwordChangeRequired()) {
            return me.isStudent() ? "redirect:" + intent.path() : back(attrs, "Only student accounts can do this.");
        }
        intent.keep(session);
        return me == null ? "redirect:/login" : "redirect:/dashboard"; // the onboarding gate picks the next step
    }

    private RideIntent intent(Kind kind, RouteForm route) {
        if (!route.complete()) {
            return new RideIntent(kind, null, null, null, null, null);
        }
        return new RideIntent(kind, route.getFrom(), route.getTo(), null,
                stop(route.getFrom()).orElseThrow().name(), stop(route.getTo()).orElseThrow().name());
    }

    private String problemWith(RouteForm route) {
        if (!route.complete()) {
            return null;
        }
        if (route.getFrom().equals(route.getTo())) {
            return "Pickup and drop must be different.";
        }
        return stop(route.getFrom()).isEmpty() || stop(route.getTo()).isEmpty()
                ? "That stop is not in service any more." : null;
    }

    private Optional<LocationOption> stop(UUID id) {
        return campus.activeLocations().stream().filter(l -> l.id().equals(id)).findFirst();
    }

    private static String back(RedirectAttributes attrs, String message) {
        Flash.error(attrs, message);
        return "redirect:/rides";
    }
}
