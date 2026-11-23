package in.ggv.campora.identity.web;

import java.util.Optional;
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
import in.ggv.campora.identity.security.ActiveSessions;
import in.ggv.campora.identity.security.CamporaUserDetails;
import in.ggv.campora.identity.service.AccountService;
import in.ggv.campora.identity.service.OtpService;
import in.ggv.campora.web.form.ResetPasswordForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** Forgotten password: email a code, then set a new password with it. */
@Controller
public class PasswordController {

    private static final String RESET_EMAIL = "resetEmail";

    private final AccountService accounts;
    private final ActiveSessions sessions;
    private final boolean exposeOtp;

    public PasswordController(AccountService accounts, ActiveSessions sessions, CamporaProperties properties) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.exposeOtp = properties.otp().exposeInUi() && properties.mail().from().isBlank();
    }

    @GetMapping("/forgot")
    public String forgotPage(@AuthenticationPrincipal CamporaUserDetails me) {
        return me != null ? "redirect:/dashboard" : "auth/forgot";
    }

    @PostMapping("/forgot")
    public String sendCode(@AuthenticationPrincipal CamporaUserDetails me, @RequestParam("email") String email,
                           HttpSession session, RedirectAttributes attrs) {
        if (me != null) {
            return "redirect:/dashboard";
        }
        Optional<OtpService.IssuedOtp> otp;
        try {
            otp = accounts.sendPasswordReset(email);
        } catch (BusinessException e) {
            Flash.error(attrs, e.getMessage());
            return "redirect:/forgot";
        }
        session.setAttribute(RESET_EMAIL, email.trim());
        if (exposeOtp && otp.isPresent()) {
            attrs.addFlashAttribute("devOtp", otp.get().code());
        } else {
            // Same words whether or not the account exists.
            Flash.success(attrs, "If that email has an account, we've sent it a 6-digit code.");
        }
        return "redirect:/reset";
    }

    @GetMapping("/reset")
    public String resetPage(@AuthenticationPrincipal CamporaUserDetails me, HttpSession session, Model model) {
        if (me != null) {
            return "redirect:/dashboard";
        }
        if (!(session.getAttribute(RESET_EMAIL) instanceof String email)) {
            return "redirect:/forgot";
        }
        model.addAttribute("email", email);
        model.addAttribute("form", new ResetPasswordForm());
        return "auth/reset";
    }

    @PostMapping("/reset")
    public String reset(@AuthenticationPrincipal CamporaUserDetails me,
                        @Valid @ModelAttribute("form") ResetPasswordForm form, BindingResult result,
                        HttpSession session, Model model, RedirectAttributes attrs) {
        if (me != null) {
            return "redirect:/dashboard";
        }
        if (!(session.getAttribute(RESET_EMAIL) instanceof String email)) {
            return "redirect:/forgot";
        }
        model.addAttribute("email", email);
        if (result.hasErrors()) {
            return "auth/reset";
        }
        UUID userId;
        try {
            userId = accounts.resetPassword(email, form.getCode(), form.getPassword());
        } catch (BusinessException e) {
            result.reject("reset", e.getMessage());
            return "auth/reset";
        }
        session.removeAttribute(RESET_EMAIL);
        sessions.signOutEverywhere(userId);
        Flash.success(attrs, "Password changed. Sign in with your new password.");
        return "redirect:/login";
    }
}
