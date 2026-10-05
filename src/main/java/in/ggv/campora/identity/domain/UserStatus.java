package in.ggv.campora.identity.domain;

public enum UserStatus {
    /** Registered, mobile number not yet confirmed by OTP. */
    PENDING_VERIFICATION,
    ACTIVE,
    /** Blocked by an administrator; cannot sign in. */
    SUSPENDED
}
