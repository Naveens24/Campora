package in.ggv.campora.identity.security;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import in.ggv.campora.identity.repository.UserRepository;

/** Remembers when each account last signed in, so admins can tell active accounts from stale ones. */
@Component
public class SignInRecorder {

    private final UserRepository users;
    private final Clock clock;

    public SignInRecorder(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @EventListener
    public void onFormLogin(InteractiveAuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof CamporaUserDetails user) {
            record(user.userId());
        }
    }

    public void record(UUID userId) {
        users.recordSignIn(userId, clock.instant());
    }
}
