package in.ggv.campora.identity.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.security.AuthenticationRefresher;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.identity.service.AccountService;
import in.ggv.campora.identity.service.OnboardingService;
import in.ggv.campora.profile.domain.VehicleType;
import in.ggv.campora.web.form.DriverProfileForm;
import in.ggv.campora.web.form.StudentProfileForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Role selection and profile completion after OTP verification. */
@Controller
@RequestMapping("/onboarding")
public class OnboardingController {

    private final AccountService accounts;
    private final OnboardingService onboarding;
    private final CampusService campus;
    private final AuthenticationRefresher refresher;

    public OnboardingController(AccountService accounts, OnboardingService onboarding, CampusService campus,
                                AuthenticationRefresher refresher) {
        this.accounts = accounts;
        this.onboarding = onboarding;
        this.campus = campus;
        this.refresher = refresher;
    }

    @GetMapping("/contact")
    public String contactPage(@AuthenticationPrincipal CamporaUserDetails me) {
        return me.verified() && me.mobile() == null ? "onboarding/contact" : "redirect:/onboarding/role";
    }

    @PostMapping("/contact")
    public String saveContact(@AuthenticationPrincipal CamporaUserDetails me, @RequestParam("mobile") String mobile,
                              HttpServletRequest request, HttpServletResponse response) {
        accounts.setMobile(me.userId(), mobile);
        refresher.signIn(me.userId(), request, response);
        return "redirect:/onboarding/role";
    }

    @GetMapping("/role")
    public String rolePage(@AuthenticationPrincipal CamporaUserDetails me) {
        if (!me.verified()) {
            return "redirect:/verify";
        }
        if (me.mobile() == null) {
            return "redirect:/onboarding/contact";
        }
        return me.role() == null ? "onboarding/role" : "redirect:/onboarding/profile";
    }

    @PostMapping("/role")
    public String chooseRole(@AuthenticationPrincipal CamporaUserDetails me, @RequestParam("role") RoleName role,
                             HttpServletRequest request, HttpServletResponse response) {
        onboarding.chooseRole(me.userId(), role);
        refresher.signIn(me.userId(), request, response);
        return "redirect:/onboarding/profile";
    }

    @GetMapping("/profile")
    public String profilePage(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        if (me.role() == null) {
            return "redirect:/onboarding/role";
        }
        if (me.profileComplete()) {
            return "redirect:/dashboard";
        }
        if (me.isDriver()) {
            model.addAttribute("form", new DriverProfileForm());
            return driverPage(model);
        }
        model.addAttribute("form", new StudentProfileForm());
        return studentPage(model);
    }

    @PostMapping("/profile/student")
    public String saveStudent(@AuthenticationPrincipal CamporaUserDetails me,
                              @Valid @ModelAttribute("form") StudentProfileForm form, BindingResult result,
                              Model model, HttpServletRequest request, HttpServletResponse response,
                              RedirectAttributes attrs) {
        if (result.hasErrors()) {
            return studentPage(model);
        }
        onboarding.createStudentProfile(me.userId(), form.getDepartmentId(), form.getSemester());
        refresher.signIn(me.userId(), request, response);
        Flash.success(attrs, "Welcome aboard, " + me.firstName() + "! Where are you heading?");
        return "redirect:/student";
    }

    @PostMapping("/profile/driver")
    public String saveDriver(@AuthenticationPrincipal CamporaUserDetails me,
                             @Valid @ModelAttribute("form") DriverProfileForm form, BindingResult result,
                             Model model, HttpServletRequest request, HttpServletResponse response,
                             RedirectAttributes attrs) {
        if (result.hasErrors()) {
            return driverPage(model);
        }
        onboarding.createDriverProfile(me.userId(), form.getVehicleType(), form.getVehicleNumber(),
                form.getSeatCapacity(), form.getNativeArea());
        refresher.signIn(me.userId(), request, response);
        Flash.success(attrs, "You're all set! Go online to start taking rides.");
        return "redirect:/driver";
    }

    private String studentPage(Model model) {
        model.addAttribute("departments", campus.activeDepartments());
        return "onboarding/student-profile";
    }

    private String driverPage(Model model) {
        model.addAttribute("vehicleTypes", VehicleType.values());
        return "onboarding/driver-profile";
    }
}
