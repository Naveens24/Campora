package in.ggv.campora.identity.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import in.ggv.campora.identity.domain.RoleName;

/**
 * The signed-in principal. Carries the onboarding flags the
 * {@link OnboardingInterceptor} needs, so no query runs per request.
 *
 * @param role null until the user selects one during onboarding
 */
public record CamporaUserDetails(
        UUID userId,
        String mobile,
        String passwordHash,
        String firstName,
        String fullName,
        RoleName role,
        boolean mobileVerified,
        boolean profileComplete,
        boolean suspended) implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Authority for users who have not chosen a role yet. */
    public static final String ONBOARDING = "ROLE_ONBOARDING";

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role == null ? ONBOARDING : role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return mobile;
    }

    @Override
    public boolean isEnabled() {
        return !suspended;
    }

    /** Fully onboarded and allowed into the role dashboards. */
    public boolean ready() {
        return mobileVerified && role != null && profileComplete;
    }

    public boolean isStudent() {
        return role == RoleName.STUDENT;
    }

    public boolean isDriver() {
        return role == RoleName.DRIVER;
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }
}
