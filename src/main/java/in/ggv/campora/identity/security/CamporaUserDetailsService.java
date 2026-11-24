package in.ggv.campora.identity.security;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.service.MobileNumbers;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;

/** Loads users by email or mobile number for Spring Security's form login. */
@Service
public class CamporaUserDetailsService implements UserDetailsService {

    private final UserRepository users;
    private final StudentProfileRepository students;
    private final DriverProfileRepository drivers;

    public CamporaUserDetailsService(UserRepository users, StudentProfileRepository students,
                                     DriverProfileRepository drivers) {
        this.users = users;
        this.students = students;
        this.drivers = drivers;
    }

    /** @param login email address or mobile number */
    @Override
    @Transactional(readOnly = true)
    public CamporaUserDetails loadUserByUsername(String login) {
        Optional<User> user;
        if (login.contains("@")) {
            user = users.findByEmailWithRole(login.trim().toLowerCase(Locale.ROOT));
        } else {
            try {
                user = users.findByMobileWithRole(MobileNumbers.normalize(login));
            } catch (BusinessException e) {
                throw new UsernameNotFoundException("Invalid mobile number");
            }
        }
        return details(user.orElseThrow(() -> new UsernameNotFoundException("No such account")));
    }

    @Transactional(readOnly = true)
    public CamporaUserDetails loadById(UUID userId) {
        return details(users.findByIdWithRole(userId).orElseThrow(() -> new NotFoundException("User")));
    }

    private CamporaUserDetails details(User user) {
        RoleName role = user.getRole() == null ? null : user.getRole().getName();
        boolean profileComplete = role != null && switch (role) {
            case ADMIN -> true;
            case STUDENT -> students.existsByUserId(user.getId());
            case DRIVER -> drivers.existsByUserId(user.getId());
        };

        return new CamporaUserDetails(user.getId(), user.getEmail(), user.getMobileNumber(),
                user.getPasswordHash(),
                user.getFirstName(), user.getFullName(), role, user.isVerified(), profileComplete,
                !user.getStatus().canSignIn(), user.isPasswordChangeRequired());
    }
}
