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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.campus.service.DestinationRequestService;
import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.companion.service.CompanionService;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.dto.StudentRequestView;
import in.ggv.campora.mobility.service.TravelRequestService;
import in.ggv.campora.trip.service.JourneyService;
import in.ggv.campora.web.form.DestinationProposalForm;
import in.ggv.campora.web.form.TravelRequestForm;
import jakarta.validation.Valid;

/** Student console: raise a travel request, follow it live, see history. */
@Controller
@RequestMapping("/student")
public class StudentController {

    private static final int HISTORY_LIMIT = 30;

    private final TravelRequestService requests;
    private final CompanionService companions;
    private final JourneyService journeys;
    private final DestinationRequestService proposals;
    private final CampusService campus;
    private final CamporaProperties properties;

    public StudentController(TravelRequestService requests, CompanionService companions, JourneyService journeys,
                             DestinationRequestService proposals, CampusService campus,
                             CamporaProperties properties) {
        this.requests = requests;
        this.companions = companions;
        this.journeys = journeys;
        this.proposals = proposals;
        this.campus = campus;
        this.properties = properties;
    }

    @GetMapping
    public String dashboard(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        StudentRequestView active = requests.activeRequest(me.userId()).orElse(null);
        model.addAttribute("active", active);
        model.addAttribute("joinedRide", companions.joinedRide(me.userId()).orElse(null));
        model.addAttribute("hostedRide", companions.hostedRide(me.userId()).orElse(null));
        model.addAttribute("rides", companions.joinableRides(me.userId()).stream().limit(4).toList());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TravelRequestForm());
        }
        model.addAttribute("locations", campus.activeLocations());
        model.addAttribute("requestTypes", RequestType.values());
        model.addAttribute("maxSeats", properties.mobility().maxSeatsPerRequest());
        return "student/dashboard";
    }

    @PostMapping("/requests")
    public String create(@AuthenticationPrincipal CamporaUserDetails me,
                         @Valid @ModelAttribute("form") TravelRequestForm form, BindingResult result,
                         RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
            return "redirect:/student";
        }
        requests.create(me.userId(), form.getPickupId(), form.getDestinationId(),
                form.getRequestType(), form.getSeatsRequired());
        Flash.success(attrs, "You're on the board. Drivers heading your way can now see this demand.");
        return "redirect:/student";
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancel(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                         RedirectAttributes attrs) {
        requests.cancel(me.userId(), id);
        Flash.success(attrs, "Request cancelled.");
        return "redirect:/student";
    }

    @GetMapping("/history")
    public String history(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        model.addAttribute("journeys", journeys.studentJourneys(me.userId(), HISTORY_LIMIT));
        model.addAttribute("requests", requests.history(me.userId(), HISTORY_LIMIT));
        return "student/history";
    }

    @GetMapping("/suggest")
    public String suggestPage(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DestinationProposalForm());
        }
        model.addAttribute("mine", proposals.mine(me.userId()));
        return "student/suggest";
    }

    @PostMapping("/suggest")
    public String suggest(@AuthenticationPrincipal CamporaUserDetails me,
                          @Valid @ModelAttribute("form") DestinationProposalForm form, BindingResult result,
                          RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
            return "redirect:/student/suggest";
        }
        proposals.submit(me.userId(), form.getName(), form.getArea(), form.getReason());
        Flash.success(attrs, "Thanks! The transport office will review \"" + form.getName().trim() + "\".");
        return "redirect:/student/suggest";
    }
}
