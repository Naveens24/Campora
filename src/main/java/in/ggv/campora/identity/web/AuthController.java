package in.ggv.campora.identity.web;

import java.util.UUID;

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
import in.ggv.campora.identity.security.CodeThrottle;
import in.ggv.campora.identity.service.AccountService;
import in.ggv.campora.identity.service.OtpService;
import in.ggv.campora.web.form.RegisterForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Sign in, register, and email OTP verification, and the Google button. */
@Controller
public class AuthController {

    private final AccountService accounts;
    private final AuthenticationRefresher refresher;
    private final CodeThrottle codes;
    private final boolean mailEnabled;
    /** Test mode only: never while real emails go out. */
    private final boolean exposeOtp;
    private final boolean demoAccounts;
    private final boolean googleEnabled;

    public AuthController(AccountService accounts, AuthenticationRefresher refresher, CodeThrottle codes,
                          CamporaProperties properties) {
        this.accounts = accounts;
        this.refresher = refresher;
        this.codes = codes;
        this.mailEnabled = !properties.mail().from().isBlank();
        this.exposeOtp = properties.otp().exposeInUi() && !mailEnabled;
        this.demoAccounts = properties.seedDemoData();
        this.googleEnabled = properties.google().enabled();
    }

    @ModelAttribute("googleEnabled")
    public boolean googleEnabled() {
        return googleEnabled;
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
        UUID userId;
        try {
            codes.check(request);
            userId = accounts.register(form.getFirstName(), form.getLastName(), form.getEmail(), form.getMobile(),
                    form.getPassword());
        } catch (BusinessException e) {
            result.reject("register", e.getMessage());
            return "auth/register";
        }
        CamporaUserDetails me = refresher.signIn(userId, request, response);
        try {
            sendCode(me, attrs);
        } catch (BusinessException e) {
            Flash.error(attrs, e.getMessage()); // the account exists; "Resend code" retries
        }
        return "redirect:/verify";
    }

    @GetMapping("/verify")
    public String verifyPage(@AuthenticationPrincipal CamporaUserDetails me, Model model) {
        if (me.verified()) {
            return "redirect:/dashboard";
        }
        model.addAttribute("email", me.email());
        model.addAttribute("mailEnabled", mailEnabled);
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String verify(@AuthenticationPrincipal CamporaUserDetails me, @RequestParam("code") String code,
                         HttpServletRequest request, HttpServletResponse response, RedirectAttributes attrs) {
        accounts.verifyEmail(me.userId(), code);
        refresher.signIn(me.userId(), request, response);
        Flash.success(attrs, "Email verified.");
        return "redirect:/onboarding/role";
    }

    /** Corrects a mistyped address, or adds one to an older unverified account. */
    @PostMapping("/verify/email")
    public String changeEmail(@AuthenticationPrincipal CamporaUserDetails me, @RequestParam("email") String email,
                              HttpServletRequest request, HttpServletResponse response, RedirectAttributes attrs) {
        codes.check(request);
        accounts.changePendingEmail(me.userId(), email);
        CamporaUserDetails updated = refresher.signIn(me.userId(), request, response);
        try {
            sendCode(updated, attrs);
        } catch (BusinessException e) {
            Flash.error(attrs, e.getMessage());
        }
        return "redirect:/verify";
    }

    @PostMapping("/verify/resend")
    public String resend(@AuthenticationPrincipal CamporaUserDetails me, HttpServletRequest request,
                         RedirectAttributes attrs) {
        codes.check(request);
        sendCode(me, attrs);
        return "redirect:/verify";
    }

    private void sendCode(CamporaUserDetails me, RedirectAttributes attrs) {
        OtpService.IssuedOtp otp = accounts.sendVerificationCode(me.userId());
        if (exposeOtp) {
            attrs.addFlashAttribute("devOtp", otp.code());
        } else {
            Flash.success(attrs, "We sent a 6-digit code to " + me.email() + ".");
        }
    }
}
