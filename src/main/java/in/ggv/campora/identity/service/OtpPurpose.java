package in.ggv.campora.identity.service;

/** What a one-time code unlocks. A code issued for one purpose never works for another. */
public enum OtpPurpose {
    VERIFY_EMAIL("Your Campora verification code", "verification code"),
    RESET_PASSWORD("Reset your Campora password", "password reset code");

    private final String subject;
    private final String label;

    OtpPurpose(String subject, String label) {
        this.subject = subject;
        this.label = label;
    }

    public String subject() {
        return subject;
    }

    public String label() {
        return label;
    }
}
