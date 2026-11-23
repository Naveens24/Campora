package in.ggv.campora.identity.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Swaps Google's identity for a Campora account, then signs in exactly like form login. */
@Component
public class GoogleLoginHandler implements AuthenticationSuccessHandler {

    private final AccountService accounts;
    private final CamporaUserDetailsService users;
    private final AuthenticationRefresher refresher;

    public GoogleLoginHandler(AccountService accounts, CamporaUserDetailsService users,
                              AuthenticationRefresher refresher) {
        this.accounts = accounts;
        this.users = users;
        this.refresher = refresher;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser google = (OidcUser) authentication.getPrincipal();
        if (!Boolean.TRUE.equals(google.getEmailVerified())) {
            reject(request, response, "google");
            return;
        }
        try {
            UUID userId = accounts.signInWithGoogle(google.getSubject(), google.getEmail(),
                    google.getGivenName(), google.getFamilyName());
            if (!users.loadById(userId).isEnabled()) {
                reject(request, response, "suspended");
                return;
            }
            refresher.signIn(userId, request, response);
        } catch (BusinessException | DataIntegrityViolationException e) {
            reject(request, response, "google");
            return;
        }
        response.sendRedirect(request.getContextPath() + "/dashboard");
    }

    private static void reject(HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login?" + reason);
    }
}
