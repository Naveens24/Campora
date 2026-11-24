package in.ggv.campora.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;
import in.ggv.campora.identity.domain.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Loads the user together with the role in one query (used at login). */
    @Query("select u from User u left join fetch u.role where u.mobileNumber = :mobile")
    Optional<User> findByMobileWithRole(@Param("mobile") String mobile);

    @Query("select u from User u left join fetch u.role where u.email = :email")
    Optional<User> findByEmailWithRole(@Param("email") String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByMobileNumber(String mobileNumber);

    Optional<User> findByGoogleSubject(String googleSubject);

    @Query("select u from User u left join fetch u.role where u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") UUID id);

    @Query("select count(u) from User u where u.role.name = :role")
    long countByRole(@Param("role") RoleName role);

    long countByStatus(UserStatus status);

    List<User> findTop6ByStatusNotOrderByCreatedAtDesc(UserStatus excluded);

    /** A bulk update, so recording a sign-in never clashes with an edit of the account. */
    @Transactional
    @Modifying
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void recordSignIn(@Param("id") UUID id, @Param("at") Instant at);

    /** Legacy column that is no longer mapped; cleared when an account is deleted. */
    @Modifying
    @Query(value = "update student_profile set enrollment_no = null where user_id = :id", nativeQuery = true)
    void clearEnrollmentNumber(@Param("id") UUID id);
}
