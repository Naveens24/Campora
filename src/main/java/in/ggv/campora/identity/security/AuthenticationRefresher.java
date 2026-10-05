package in.ggv.campora.identity.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Re-issues the session's authentication after an onboarding step
 * (registration, OTP, role choice, profile) so the new role and flags
 * take effect immediately, without asking the user to sign in again.
 */
@Component
public class AuthenticationRefresher {

    private final CamporaUserDetailsService userDetailsService;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public AuthenticationRefresher(CamporaUserDetailsService userDetailsService,
                                   SecurityContextRepository contextRepository) {
        this.userDetailsService = userDetailsService;
        this.contextRepository = contextRepository;
    }

    public CamporaUserDetails signIn(String mobile, HttpServletRequest request, HttpServletResponse response) {
        CamporaUserDetails principal = userDetailsService.loadUserByUsername(mobile);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        if (request.getSession(false) != null) {
            request.changeSessionId();   // session-fixation defence on privilege change
        }
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return principal;
    }
}
