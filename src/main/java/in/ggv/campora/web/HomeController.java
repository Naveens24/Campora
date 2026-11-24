package in.ggv.campora.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.web.form.RouteForm;
import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

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
        model.addAttribute("upcoming", companions.upcomingRides().size());
        return "index";
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
