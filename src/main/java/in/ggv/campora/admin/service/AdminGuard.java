package in.ggv.campora.admin.service;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.service.OwnerAccounts;

/**
 * Who may manage whom: admins manage students and drivers, only owners manage
 * admins, and nobody else touches an owner. Checked against the database on
 * every action, so a demoted admin's open session is worth nothing.
 */
@Component
public class AdminGuard {

    private final UserRepository users;
    private final OwnerAccounts owners;

    public AdminGuard(UserRepository users, OwnerAccounts owners) {
        this.users = users;
        this.owners = owners;
    }

    /** The acting admin, freshly loaded. */
    public User admin(UUID adminId) {
        User admin = users.findByIdWithRole(adminId).orElseThrow(() -> new AccessDeniedException("Not an admin"));
        if (!isAdmin(admin) || admin.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("Not an admin");
        }
        return admin;
    }

    public User user(UUID userId) {
        return users.findByIdWithRole(userId)
                .filter(user -> user.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new NotFoundException("User"));
    }

    public boolean canManage(User admin, User target) {
        if (admin.equals(target)) {
            return false;
        }
        if (owners.isOwner(target)) {
            return false;
        }
        return !isAdmin(target) || owners.isOwner(admin);
    }

    public void requireManage(User admin, User target) {
        if (admin.equals(target)) {
            throw new BusinessException("You can't do this to your own account.");
        }
        if (!canManage(admin, target)) {
            throw new BusinessException("Only the owner can manage admin accounts.");
        }
    }

    /** Like {@link #requireManage}, but people may also correct their own details. */
    public void requireEdit(User admin, User target) {
        if (!admin.equals(target)) {
            requireManage(admin, target);
        }
    }

    public boolean isOwner(User user) {
        return owners.isOwner(user);
    }

    public void requireOwner(User admin) {
        if (!owners.isOwner(admin)) {
            throw new BusinessException("Only the owner can make someone an admin.");
        }
    }

    static boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole().getName() == RoleName.ADMIN;
    }
}
