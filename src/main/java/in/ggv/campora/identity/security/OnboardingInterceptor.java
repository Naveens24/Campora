package in.ggv.campora.identity.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Walks a signed-in user through onboarding in order:
 * verify mobile → choose role → complete profile. Until all three are done,
 * every page redirects to the next pending step.
 */
@Component
public class OnboardingInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CamporaUserDetails user) || user.ready()) {
            return true;
        }
        String target;
        if (!user.mobileVerified()) {
            target = "/verify";
        } else if (user.role() == null) {
            target = "/onboarding/role";
        } else {
            target = "/onboarding/profile";
        }
        response.sendRedirect(request.getContextPath() + target);
        return false;
    }
}
