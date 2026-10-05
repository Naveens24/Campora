package in.ggv.campora.common.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import in.ggv.campora.identity.security.CamporaUserDetails;
import jakarta.servlet.http.HttpServletRequest;

/** Adds values every page layout needs: the signed-in user and the current path. */
@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAdvice {

    @ModelAttribute("me")
    public CamporaUserDetails me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CamporaUserDetails user ? user : null;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
