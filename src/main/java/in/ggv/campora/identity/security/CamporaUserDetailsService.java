package in.ggv.campora.identity.security;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.identity.repository.UserRepository;
import in.ggv.campora.identity.service.MobileNumbers;
import in.ggv.campora.profile.repository.DriverProfileRepository;
import in.ggv.campora.profile.repository.StudentProfileRepository;

/** Loads users by mobile number for Spring Security's form login. */
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

    @Override
    @Transactional(readOnly = true)
    public CamporaUserDetails loadUserByUsername(String rawMobile) {
        String mobile;
        try {
            mobile = MobileNumbers.normalize(rawMobile);
        } catch (BusinessException e) {
            throw new UsernameNotFoundException("Invalid mobile number");
        }
        User user = users.findByMobileWithRole(mobile)
                .orElseThrow(() -> new UsernameNotFoundException("No account for this mobile number"));

        RoleName role = user.getRole() == null ? null : user.getRole().getName();
        boolean profileComplete = role != null && switch (role) {
            case ADMIN -> true;
            case STUDENT -> students.existsByUserId(user.getId());
            case DRIVER -> drivers.existsByUserId(user.getId());
        };

        return new CamporaUserDetails(user.getId(), user.getMobileNumber(), user.getPasswordHash(),
                user.getFirstName(), user.getFullName(), role, user.isMobileVerified(), profileComplete,
                user.getStatus() == UserStatus.SUSPENDED);
    }
}
