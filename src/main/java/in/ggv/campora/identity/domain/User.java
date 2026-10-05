package in.ggv.campora.identity.domain;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A person who signs in. Role-specific data lives in StudentProfile or
 * DriverProfile, keeping this table small and stable.
 */
@Entity
@Table(name = "app_user")
@SQLRestriction("deleted = false")
public class User extends BaseEntity {

    /** Null until the user picks a role during onboarding. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "mobile_number", nullable = false, unique = true, length = 15)
    private String mobileNumber;

    /** BCrypt hash — the raw password is never stored. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private UserStatus status;

    @Column(name = "mobile_verified_at")
    private Instant mobileVerifiedAt;

    protected User() {
    }

    public User(String firstName, String lastName, String mobileNumber, String passwordHash) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.mobileNumber = mobileNumber;
        this.passwordHash = passwordHash;
        this.status = UserStatus.PENDING_VERIFICATION;
    }

    public void markMobileVerified(Instant at) {
        this.mobileVerifiedAt = at;
        if (status == UserStatus.PENDING_VERIFICATION) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public void assignRole(Role role) {
        this.role = role;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = mobileVerifiedAt != null ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
    }

    public boolean isMobileVerified() {
        return mobileVerifiedAt != null;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public Role getRole() {
        return role;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getMobileVerifiedAt() {
        return mobileVerifiedAt;
    }
}
