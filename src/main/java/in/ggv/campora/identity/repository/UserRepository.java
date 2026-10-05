package in.ggv.campora.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.identity.domain.RoleName;
import in.ggv.campora.identity.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Loads the user together with the role in one query (used at login). */
    @Query("select u from User u left join fetch u.role where u.mobileNumber = :mobile")
    Optional<User> findByMobileWithRole(@Param("mobile") String mobile);

    @Query("select u from User u left join fetch u.role where u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") UUID id);

    boolean existsByMobileNumber(String mobileNumber);

    @Query("select count(u) from User u where u.role.name = :role")
    long countByRole(@Param("role") RoleName role);
}
