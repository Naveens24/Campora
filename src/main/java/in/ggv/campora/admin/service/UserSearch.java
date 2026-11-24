package in.ggv.campora.admin.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import in.ggv.campora.admin.dto.UserView;
import in.ggv.campora.identity.domain.Role;
import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;
import in.ggv.campora.profile.domain.DriverProfile;
import in.ggv.campora.profile.domain.StudentProfile;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Filters and free-text search for the admin user list: name, email, mobile or number plate. */
final class UserSearch {

    private static final char ESCAPE = '!';

    private UserSearch() {
    }

    static Specification<User> matching(UserView view, String text) {
        return (user, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.notEqual(user.get("status"), UserStatus.DELETED));
            Join<User, Role> role = user.join("role", JoinType.LEFT);
            Expression<RoleName> roleName = role.get("name");
            switch (view == null ? UserView.ALL : view) {
                case ALL -> { }
                case STUDENTS -> all.add(cb.equal(roleName, RoleName.STUDENT));
                case DRIVERS -> all.add(cb.equal(roleName, RoleName.DRIVER));
                case ADMINS -> all.add(cb.equal(roleName, RoleName.ADMIN));
                case SUSPENDED -> all.add(cb.equal(user.get("status"), UserStatus.SUSPENDED));
                case UNVERIFIED -> all.add(cb.equal(user.get("status"), UserStatus.PENDING_VERIFICATION));
                case UNFINISHED -> all.add(cb.and(
                        cb.notEqual(user.get("status"), UserStatus.PENDING_VERIFICATION),
                        cb.or(cb.isNull(role.get("id")),
                                cb.and(cb.equal(roleName, RoleName.STUDENT),
                                        cb.not(hasProfile(StudentProfile.class, user, query, cb))),
                                cb.and(cb.equal(roleName, RoleName.DRIVER),
                                        cb.not(hasProfile(DriverProfile.class, user, query, cb))))));
                case BLOCKED -> all.add(cb.and(cb.equal(roleName, RoleName.DRIVER), blockedDriver(user, query, cb)));
            }
            if (text != null && !text.isBlank()) {
                all.add(containsText(text.trim().toLowerCase(Locale.ROOT), user, query, cb));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsText(String text, Root<User> user, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Expression<String> name = cb.lower(cb.concat(cb.concat(user.<String>get("firstName"), " "),
                user.<String>get("lastName")));
        List<Predicate> any = new ArrayList<>();
        any.add(like(cb, name, text));
        any.add(like(cb, user.get("email"), text));
        String digits = text.replaceAll("\\D", "");
        if (digits.length() >= 3 && text.matches("[+\\d\\s-]+")) {
            boolean withCountryCode = digits.length() == 12 && digits.startsWith("91");
            any.add(like(cb, user.get("mobileNumber"), withCountryCode ? digits.substring(2) : digits));
        }
        Subquery<Integer> plate = query.subquery(Integer.class);
        Root<DriverProfile> driver = plate.from(DriverProfile.class);
        plate.select(cb.literal(1)).where(cb.equal(driver.get("user"), user),
                like(cb, cb.lower(driver.get("vehicleNumber")), text.replaceAll("\\s+", "")));
        any.add(cb.exists(plate));
        return cb.or(any.toArray(Predicate[]::new));
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> field, String text) {
        String safe = text.replaceAll("([!%_])", ESCAPE + "$1");
        return cb.like(field, '%' + safe + '%', ESCAPE);
    }

    private static Predicate hasProfile(Class<?> profile, Root<User> user, CriteriaQuery<?> query,
                                        CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<?> root = sub.from(profile);
        sub.select(cb.literal(1)).where(cb.equal(root.get("user"), user));
        return cb.exists(sub);
    }

    private static Predicate blockedDriver(Root<User> user, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> sub = query.subquery(Integer.class);
        Root<DriverProfile> driver = sub.from(DriverProfile.class);
        sub.select(cb.literal(1)).where(cb.equal(driver.get("user"), user), cb.isFalse(driver.get("verified")));
        return cb.exists(sub);
    }
}
