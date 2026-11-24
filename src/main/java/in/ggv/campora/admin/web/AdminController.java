package in.ggv.campora.admin.web;

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

import in.ggv.campora.admin.service.AdminLog;
import in.ggv.campora.admin.service.AdminService;
import in.ggv.campora.admin.service.RideAdminService;
import in.ggv.campora.admin.service.UserAdminService;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.campus.service.DestinationRequestService;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.web.form.DepartmentForm;
import in.ggv.campora.web.form.LocationForm;
import jakarta.validation.Valid;

/** Admin panel: overview, live rides, stops and departments, activity. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;
    private final UserAdminService users;
    private final RideAdminService rides;
    private final AdminLog log;
    private final CampusService campus;
    private final DestinationRequestService proposals;

    public AdminController(AdminService admin, UserAdminService users, RideAdminService rides, AdminLog log,
                           CampusService campus, DestinationRequestService proposals) {
        this.admin = admin;
        this.users = users;
        this.rides = rides;
        this.log = log;
        this.campus = campus;
        this.proposals = proposals;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("stats", admin.stats());
        model.addAttribute("newest", users.newest());
        model.addAttribute("actions", log.recent(0, 5).getContent());
        return "admin/overview";
    }

    // ── rides ──────────────────────────────────────────────────

    @GetMapping("/rides")
    public String rides(Model model) {
        model.addAttribute("live", rides.live());
        return "admin/rides";
    }

    @PostMapping("/rides/trips/{id}/end")
    public String endTrip(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                          RedirectAttributes attrs) {
        rides.endTrip(me.userId(), id);
        Flash.success(attrs, "Trip ended.");
        return "redirect:/admin/rides";
    }

    @PostMapping("/rides/companion/{id}/end")
    public String endCompanion(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                               RedirectAttributes attrs) {
        rides.endCompanionRide(me.userId(), id);
        Flash.success(attrs, "Companion ride ended.");
        return "redirect:/admin/rides";
    }

    @PostMapping("/rides/requests/{id}/cancel")
    public String cancelRequest(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                                RedirectAttributes attrs) {
        rides.cancelRequest(me.userId(), id);
        Flash.success(attrs, "Request cancelled.");
        return "redirect:/admin/rides";
    }

    // ── stops and departments ──────────────────────────────────

    @GetMapping("/stops")
    public String stops(Model model) {
        model.addAttribute("suggestions", proposals.pending());
        model.addAttribute("locations", campus.allLocations());
        model.addAttribute("locationTypes", LocationType.values());
        return "admin/stops";
    }

    @GetMapping("/departments")
    public String departments(Model model) {
        model.addAttribute("departments", campus.allDepartments());
        return "admin/departments";
    }

    @PostMapping("/suggestions/{id}/approve")
    public String approve(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                          @RequestParam("type") LocationType type, RedirectAttributes attrs) {
        admin.approveSuggestion(me.userId(), id, type);
        Flash.success(attrs, "Suggestion approved. The stop is live.");
        return "redirect:/admin/stops";
    }

    @PostMapping("/suggestions/{id}/reject")
    public String reject(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                         @RequestParam(name = "note", required = false) String note, RedirectAttributes attrs) {
        admin.rejectSuggestion(me.userId(), id, note);
        Flash.success(attrs, "Suggestion rejected.");
        return "redirect:/admin/stops";
    }

    @PostMapping("/stops")
    public String addStop(@AuthenticationPrincipal CamporaUserDetails me, @Valid @ModelAttribute LocationForm form,
                          BindingResult result, RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
        } else {
            admin.addStop(me.userId(), form.getName(), form.getArea(), form.getType());
            Flash.success(attrs, "Stop \"" + form.getName().trim() + "\" added.");
        }
        return "redirect:/admin/stops";
    }

    @PostMapping("/stops/{id}")
    public String editStop(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                           @Valid @ModelAttribute LocationForm form, BindingResult result, RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
        } else {
            admin.renameStop(me.userId(), id, form.getName(), form.getArea());
            Flash.success(attrs, "Stop saved.");
        }
        return "redirect:/admin/stops";
    }

    @PostMapping("/stops/{id}/toggle")
    public String toggleStop(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                             RedirectAttributes attrs) {
        admin.toggleStop(me.userId(), id);
        Flash.success(attrs, "Stop updated.");
        return "redirect:/admin/stops";
    }

    @PostMapping("/departments")
    public String addDepartment(@AuthenticationPrincipal CamporaUserDetails me,
                                @Valid @ModelAttribute DepartmentForm form, BindingResult result,
                                RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
        } else {
            admin.addDepartment(me.userId(), form.getCode(), form.getName());
            Flash.success(attrs, "Department added.");
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/{id}")
    public String editDepartment(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                                 @RequestParam("name") String name, RedirectAttributes attrs) {
        if (name.isBlank() || name.trim().length() > 120) {
            Flash.error(attrs, "Enter a department name of up to 120 characters.");
        } else {
            admin.renameDepartment(me.userId(), id, name);
            Flash.success(attrs, "Department saved.");
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/{id}/toggle")
    public String toggleDepartment(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                                   RedirectAttributes attrs) {
        admin.toggleDepartment(me.userId(), id);
        Flash.success(attrs, "Department updated.");
        return "redirect:/admin/departments";
    }

    // ── activity ───────────────────────────────────────────────

    @GetMapping("/activity")
    public String activity(@RequestParam(name = "page", defaultValue = "1") int page, Model model) {
        model.addAttribute("actions", log.recent(Math.max(1, page) - 1, 30));
        return "admin/activity";
    }
}
