package in.ggv.campora.admin.web;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
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

import in.ggv.campora.admin.dto.UserDetail;
import in.ggv.campora.admin.dto.UserRow;
import in.ggv.campora.admin.dto.UserView;
import in.ggv.campora.admin.service.AdminLog;
import in.ggv.campora.admin.service.RideAdminService;
import in.ggv.campora.admin.service.UserAdminService;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.security.ActiveSessions;
import in.ggv.campora.identity.security.AuthenticationRefresher;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.mobility.service.TravelRequestService;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.trip.service.JourneyService;
import in.ggv.campora.trip.service.TripService;
import in.ggv.campora.web.form.AccountForm;
import in.ggv.campora.web.form.DriverProfileForm;
import in.ggv.campora.web.form.NewAccountForm;
import in.ggv.campora.web.form.StudentProfileForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Admin panel: find and support individual accounts. */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final int HISTORY = 20;

    private final UserAdminService users;
    private final RideAdminService rides;
    private final AdminLog log;
    private final CampusService campus;
    private final TravelRequestService requests;
    private final JourneyService journeys;
    private final TripService trips;
    private final ActiveSessions sessions;
    private final AuthenticationRefresher refresher;

    public AdminUserController(UserAdminService users, RideAdminService rides, AdminLog log, CampusService campus,
                               TravelRequestService requests, JourneyService journeys, TripService trips,
                               ActiveSessions sessions, AuthenticationRefresher refresher) {
        this.users = users;
        this.rides = rides;
        this.log = log;
        this.campus = campus;
        this.requests = requests;
        this.journeys = journeys;
        this.trips = trips;
        this.sessions = sessions;
        this.refresher = refresher;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", defaultValue = "") String q,
                       @RequestParam(name = "show", defaultValue = "ALL") UserView show,
                       @RequestParam(name = "page", defaultValue = "1") int page, Model model) {
        Page<UserRow> found = users.search(show, q, page - 1);
        model.addAttribute("users", found);
        model.addAttribute("q", q.trim());
        model.addAttribute("show", show);
        model.addAttribute("views", UserView.values());
        return "admin/users";
    }

    // ── add ────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newPage(Model model) {
        model.addAttribute("form", new NewAccountForm());
        return "admin/user-new";
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal CamporaUserDetails me,
                         @Valid @ModelAttribute("form") NewAccountForm form, BindingResult result,
                         RedirectAttributes attrs) {
        if (result.hasErrors()) {
            return "admin/user-new";
        }
        UserAdminService.NewAccount created;
        try {
            created = users.create(me.userId(), form.getFirstName(), form.getLastName(), form.getEmail(),
                    form.getMobile(), form.getRole());
        } catch (BusinessException e) {
            result.reject("create", e.getMessage());
            return "admin/user-new";
        }
        attrs.addFlashAttribute("temporaryPassword", created.temporaryPassword());
        Flash.success(attrs, "Account created.");
        return page(created.userId(), "access");
    }

    // ── one account ────────────────────────────────────────────

    @GetMapping("/{id}")
    public String show(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                       @RequestParam(name = "tab", defaultValue = "profile") String tab, Model model) {
        UserDetail user = users.detail(me.userId(), id);
        model.addAttribute("u", user);
        switch (tab) {
            case "rides" -> {
                model.addAttribute("live", rides.liveFor(id));
                if (user.student() != null) {
                    model.addAttribute("journeys", journeys.studentJourneys(id, HISTORY));
                    model.addAttribute("requests", requests.history(id, HISTORY));
                }
                if (user.driver() != null) {
                    model.addAttribute("trips", trips.history(id, HISTORY));
                }
            }
            case "access" -> { }
            case "history" -> model.addAttribute("actions", log.about(id, 50).getContent());
            default -> {
                tab = "profile";
                model.addAttribute("departments", campus.allDepartments());
                model.addAttribute("vehicleTypes", VehicleType.values());
            }
        }
        model.addAttribute("tab", tab);
        return "admin/user";
    }

    @PostMapping("/{id}/account")
    public String saveAccount(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                              @Valid @ModelAttribute("form") AccountForm form, BindingResult result,
                              HttpServletRequest request, HttpServletResponse response, RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
            return page(id, "profile");
        }
        users.updateAccount(me.userId(), id, form.getFirstName(), form.getLastName(), form.getEmail(),
                form.getMobile());
        if (id.equals(me.userId())) {
            refresher.signIn(id, request, response);
        }
        Flash.success(attrs, "Details saved.");
        return page(id, "profile");
    }

    @PostMapping("/{id}/student")
    public String saveStudent(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                              @Valid @ModelAttribute("form") StudentProfileForm form, BindingResult result,
                              RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
            return page(id, "profile");
        }
        if (users.saveStudent(me.userId(), id, form.getDepartmentId(), form.getSemester())) {
            sessions.signOutEverywhere(id);
        }
        Flash.success(attrs, "Student details saved.");
        return page(id, "profile");
    }

    @PostMapping("/{id}/driver")
    public String saveDriver(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                             @Valid @ModelAttribute("form") DriverProfileForm form, BindingResult result,
                             RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
            return page(id, "profile");
        }
        if (users.saveDriver(me.userId(), id, form.getVehicleType(), form.getVehicleNumber(), form.getSeatCapacity(),
                form.getNativeArea())) {
            sessions.signOutEverywhere(id);
        }
        Flash.success(attrs, "Driver details saved.");
        return page(id, "profile");
    }

    @PostMapping("/{id}/clear-rides")
    public String clearRides(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                             RedirectAttributes attrs) {
        rides.clear(me.userId(), id);
        Flash.success(attrs, "Active rides cleared.");
        return page(id, "rides");
    }

    // ── access ─────────────────────────────────────────────────

    @PostMapping("/{id}/temporary-password")
    public String temporaryPassword(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                                    RedirectAttributes attrs) {
        attrs.addFlashAttribute("temporaryPassword", users.setTemporaryPassword(me.userId(), id));
        sessions.signOutEverywhere(id);
        return page(id, "access");
    }

    @PostMapping("/{id}/sign-out")
    public String signOut(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                          RedirectAttributes attrs) {
        users.logSignOut(me.userId(), id);
        sessions.signOutEverywhere(id);
        Flash.success(attrs, "Signed out on all devices.");
        return page(id, "access");
    }

    @PostMapping("/{id}/verify")
    public String verify(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                         RedirectAttributes attrs) {
        users.markVerified(me.userId(), id);
        sessions.signOutEverywhere(id);
        Flash.success(attrs, "Account verified. They can sign in and continue.");
        return page(id, "access");
    }

    @PostMapping("/{id}/disconnect-google")
    public String disconnectGoogle(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                                   RedirectAttributes attrs) {
        users.disconnectGoogle(me.userId(), id);
        sessions.signOutEverywhere(id);
        Flash.success(attrs, "Google sign-in disconnected.");
        return page(id, "access");
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                          @RequestParam(name = "reason", required = false) String reason, RedirectAttributes attrs) {
        users.suspend(me.userId(), id, reason);
        sessions.signOutEverywhere(id);
        Flash.success(attrs, "Account suspended and signed out.");
        return page(id, "access");
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                             RedirectAttributes attrs) {
        users.reactivate(me.userId(), id);
        Flash.success(attrs, "Account reactivated.");
        return page(id, "access");
    }

    @PostMapping("/{id}/driving")
    public String driving(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                          @RequestParam("blocked") boolean blocked, RedirectAttributes attrs) {
        users.setDrivingBlocked(me.userId(), id, blocked);
        Flash.success(attrs, blocked ? "Blocked from driving and set offline." : "Allowed to drive again.");
        return page(id, "access");
    }

    @PostMapping("/{id}/role")
    public String role(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                       @RequestParam("role") RoleName role, RedirectAttributes attrs) {
        users.changeRole(me.userId(), id, role);
        sessions.signOutEverywhere(id);
        Flash.success(attrs, "Role changed to " + role.name().toLowerCase(Locale.ROOT) + ".");
        return page(id, "access");
    }

    // ── delete ─────────────────────────────────────────────────

    @GetMapping("/{id}/delete")
    public String deletePage(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id, Model model) {
        UserDetail user = users.detail(me.userId(), id);
        if (!user.manageable()) {
            return page(id, "access");
        }
        model.addAttribute("u", user);
        return "admin/user-delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal CamporaUserDetails me, @PathVariable UUID id,
                         @RequestParam(name = "confirm", defaultValue = "") String confirm,
                         RedirectAttributes attrs) {
        if (!"DELETE".equalsIgnoreCase(confirm.trim())) {
            Flash.error(attrs, "Type DELETE to confirm.");
            return "redirect:/admin/users/" + id + "/delete";
        }
        users.delete(me.userId(), id);
        sessions.signOutEverywhere(id);
        Flash.success(attrs, "Account deleted.");
        return "redirect:/admin/users";
    }

    private static String page(UUID id, String tab) {
        return "redirect:/admin/users/" + id + "?tab=" + tab;
    }
}
