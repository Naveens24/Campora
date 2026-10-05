package in.ggv.campora.identity.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.identity.security.AuthenticationRefresher;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.identity.service.AccountService;
import in.ggv.campora.identity.service.MobileNumbers;
import in.ggv.campora.identity.service.OtpService;
import in.ggv.campora.web.form.RegisterForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Sign in, register, and mobile OTP verification. */
@Controller
public class AuthController {

    private final AccountService accounts;
    private final AuthenticationRefresher refresher;
    private final boolean exposeOtp;
    private final boolean demoAccounts;

    public AuthController(AccountService accounts, AuthenticationRefresher refresher, CamporaProperties properties) {
        this.accounts = accounts;
        this.refresher = refresher;
        this.exposeOtp = properties.otp().exposeInUi();
        this.demoAccounts = properties.seedDemoData();
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        if (me != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("demoAccounts", demoAccounts);
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        if (me != null) {
            return "redirect:/dashboard";
        }
        model.addAttribute("form", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult result,
                           HttpServletRequest request, HttpServletResponse response, RedirectAttributes attrs) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        String mobile;
        try {
            mobile = accounts.register(form.getFirstName(), form.getLastName(), form.getMobile(), form.getPassword());
        } catch (BusinessException e) {
            result.reject("register", e.getMessage());
            return "auth/register";
        }
        CamporaUserDetails me = refresher.signIn(mobile, request, response);
        sendCode(me, attrs);
        return "redirect:/verify";
    }

    @GetMapping("/verify")
    public String verifyPage(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        if (me.mobileVerified()) {
            return "redirect:/dashboard";
        }
        model.addAttribute("maskedMobile", MobileNumbers.mask(me.mobile()));
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String verify(@AuthenticationPrincipal CamporaUserDetails me, @RequestParam("code") String code,
                         HttpServletRequest request, HttpServletResponse response, RedirectAttributes attrs) {
        accounts.verifyMobile(me.userId(), code);
        refresher.signIn(me.mobile(), request, response);
        Flash.success(attrs, "Mobile number verified.");
        return "redirect:/onboarding/role";
    }

    @PostMapping("/verify/resend")
    public String resend(@AuthenticationPrincipal CamporaUserDetails me, RedirectAttributes attrs) {
        sendCode(me, attrs);
        return "redirect:/verify";
    }

    private void sendCode(CamporaUserDetails me, RedirectAttributes attrs) {
        OtpService.IssuedOtp otp = accounts.sendVerificationCode(me.userId());
        Flash.success(attrs, "We sent a 6-digit code to " + MobileNumbers.mask(me.mobile()) + ".");
        if (exposeOtp) {
            attrs.addFlashAttribute("devOtp", otp.code());
        }
    }
}
