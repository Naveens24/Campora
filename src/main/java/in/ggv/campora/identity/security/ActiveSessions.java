package in.ggv.campora.identity.security;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/** Tracks live sessions so a password reset or an admin can sign an account out on every device. */
@Component
public class ActiveSessions implements HttpSessionListener, HttpSessionAttributeListener {

    /** Visitors get the short default; someone signed in stays signed in through the day. */
    private static final Duration SIGNED_IN = Duration.ofHours(12);

    private final Set<HttpSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        sessions.add(event.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        sessions.remove(event.getSession());
    }

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        if (HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY.equals(event.getName())) {
            event.getSession().setMaxInactiveInterval((int) SIGNED_IN.toSeconds());
        }
    }

    public void signOutEverywhere(UUID userId) {
        for (HttpSession session : sessions) {
            try {
                Object context = session.getAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if (context instanceof SecurityContext security && security.getAuthentication() != null
                        && security.getAuthentication().getPrincipal() instanceof CamporaUserDetails user
                        && user.userId().equals(userId)) {
                    session.invalidate();
                }
            } catch (IllegalStateException alreadyInvalid) {
                sessions.remove(session);
            }
        }
    }
}
