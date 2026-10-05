package in.ggv.campora.web;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.web.form.OfferRideForm;
import jakarta.validation.Valid;

/**
 * Companion rides. Not a separate role: any student can offer or join.
 */
@Controller
@RequestMapping("/companion")
public class CompanionController {

    private final CompanionService companions;
    private final CampusService campus;
    private final CamporaProperties properties;

    public CompanionController(CompanionService companions, CampusService campus, CamporaProperties properties) {
        this.companions = companions;
        this.campus = campus;
        this.properties = properties;
    }

    @GetMapping
    public String page(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        model.addAttribute("hostedRide", companions.hostedRide(me.userId()).orElse(null));
        model.addAttribute("joinedRide", companions.joinedRide(me.userId()).orElse(null));
        model.addAttribute("rides", companions.joinableRides(me.userId()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new OfferRideForm());
        }
        model.addAttribute("locations", campus.activeLocations());
        model.addAttribute("maxSeats", properties.companion().maxSeats());
        return "companion/index";
    }

    @PostMapping("/rides")
    public String offer(@AuthenticationPrincipal CamporaUserDetails me,
                        @Valid @ModelAttribute("form") OfferRideForm form, BindingResult result,
                        RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
            return "redirect:/companion";
        }
        companions.offer(me.userId(), form.getPickupId(), form.getDestinationId(), form.getDepartureTime(),
                form.getTotalSeats(), form.getNote());
        Flash.success(attrs, "Your ride is live. Students on this route can now join.");
        return "redirect:/companion";
    }

    @PostMapping("/rides/{id}/join")
    public String join(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                       @RequestParam(name = "seats", defaultValue = "1") int seats, RedirectAttributes attrs) {
        companions.join(me.userId(), id, seats);
        Flash.success(attrs, "You're in! The host's number is on your ride card.");
        return "redirect:/companion";
    }

    @PostMapping("/rides/{id}/leave")
    public String leave(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                        RedirectAttributes attrs) {
        companions.leave(me.userId(), id);
        Flash.success(attrs, "You left the ride.");
        return "redirect:/companion";
    }

    @PostMapping("/rides/{id}/start")
    public String start(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                        RedirectAttributes attrs) {
        companions.start(me.userId(), id);
        Flash.success(attrs, "Ride started. Travel safe!");
        return "redirect:/companion";
    }

    @PostMapping("/rides/{id}/complete")
    public String complete(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                           RedirectAttributes attrs) {
        companions.complete(me.userId(), id);
        Flash.success(attrs, "Ride completed and saved to history.");
        return "redirect:/companion";
    }

    @PostMapping("/rides/{id}/cancel")
    public String cancel(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                         RedirectAttributes attrs) {
        companions.cancel(me.userId(), id);
        Flash.success(attrs, "Ride cancelled. Riders with requests were returned to the board.");
        return "redirect:/companion";
    }
}
