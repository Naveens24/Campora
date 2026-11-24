package in.ggv.campora.admin.dto;

/** The filters on the admin user list. */
public enum UserView {
    ALL("All"),
    STUDENTS("Students"),
    DRIVERS("Drivers"),
    ADMINS("Admins"),
    UNVERIFIED("Email not verified"),
    UNFINISHED("Setup not finished"),
    SUSPENDED("Suspended"),
    BLOCKED("Blocked drivers");

    private final String label;

    UserView(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
