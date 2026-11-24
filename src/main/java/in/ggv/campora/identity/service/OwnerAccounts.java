package in.ggv.campora.identity.service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import in.ggv.campora.common.config.CamporaProperties;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.repository.RoleRepository;

/**
 * Owners of the admin panel, named on the server by {@code ADMIN_EMAIL}.
 * Only a proven address counts, so nobody becomes an owner just by typing it.
 */
@Component
public class OwnerAccounts {

    private final Set<String> emails;
    private final RoleRepository roles;

    public OwnerAccounts(CamporaProperties properties, RoleRepository roles) {
        this.emails = properties.admin().emails().stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.roles = roles;
    }

    public Set<String> emails() {
        return emails;
    }

    public boolean isOwnerEmail(String email) {
        return email != null && emails.contains(email);
    }

    public boolean isOwner(User user) {
        return user.isEmailVerified() && isOwnerEmail(user.getEmail());
    }

    /** A new account skips choosing Student or Driver and goes straight to the admin panel. */
    public void adopt(User user) {
        if (user.getRole() == null && isOwner(user)) {
            makeAdmin(user);
        }
    }

    public void makeAdmin(User user) {
        user.assignRole(roles.findByName(RoleName.ADMIN).orElseThrow(() -> new NotFoundException("Role")));
    }
}
