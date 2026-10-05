package in.ggv.campora.identity.domain;

/**
 * Account types. A <em>companion</em> is deliberately not a role: it is a
 * student who chooses to offer a ride — same identity, different action.
 */
public enum RoleName {
    STUDENT,
    DRIVER,
    ADMIN;

    /** Spring Security authority string, e.g. {@code ROLE_STUDENT}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
