package in.ggv.campora.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import in.ggv.campora.campus.dto.LocationOption;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.web.form.RouteForm;
import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

    private static final int MAX_ROUTES = 8;

    public record Route(LocationOption from, LocationOption to) {
    }

    private final CampusService campus;
    private final CompanionService companions;

    public HomeController(CampusService campus, CompanionService companions) {
        this.campus = campus;
        this.companions = companions;
    }

    @GetMapping("/")
    public String landing(Model model) {
        model.addAttribute("route", new RouteForm());
        model.addAttribute("locations", campus.activeLocations());
        model.addAttribute("routes", suggestedRoutes());
        model.addAttribute("upcoming", companions.upcomingRides().size());
        return "index";
    }

    /** One route out of each campus stop, from the stops in service today. */
    private List<Route> suggestedRoutes() {
        Map<Boolean, List<LocationOption>> stops = campus.activeLocations().stream()
                .collect(Collectors.partitioningBy(LocationOption::onCampus));
        List<LocationOption> city = stops.get(false);
        if (city.isEmpty()) {
            return List.of();
        }
        List<LocationOption> onCampus = stops.get(true);
        return IntStream.range(0, Math.min(MAX_ROUTES, onCampus.size()))
                .mapToObj(i -> new Route(onCampus.get(i), city.get(i % city.size()))).toList();
    }

    /** Single post-login entry point; routes each role to its own console. */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CamporaUserDetails me, HttpSession session) {
        String studentHome = RideIntent.studentHome(session);
        return switch (me.role()) {
            case STUDENT -> "redirect:" + studentHome;
            case DRIVER -> "redirect:/driver";
            case ADMIN -> "redirect:/admin";
        };
    }
}
