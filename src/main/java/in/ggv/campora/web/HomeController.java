package in.ggv.campora.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import in.ggv.campora.identity.security.CamporaUserDetails;

@Controller
public class HomeController {

    @GetMapping("/")
    public String landing() {
        return "index";
    }

    /** Single post-login entry point; routes each role to its own console. */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal CamporaUserDetails me) {
        return switch (me.role()) {
            case STUDENT -> "redirect:/student";
            case DRIVER -> "redirect:/driver";
            case ADMIN -> "redirect:/admin";
        };
    }
}
