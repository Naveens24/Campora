package in.ggv.campora.profile.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.profile.domain.Availability;
import in.ggv.campora.profile.domain.DriverProfile;
import jakarta.persistence.LockModeType;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    Optional<DriverProfile> findByUserId(UUID userId);

    /** Serialises actions of one driver (e.g. a double-clicked "Reserve"). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DriverProfile d where d.user.id = :userId")
    Optional<DriverProfile> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Query("select d from DriverProfile d join fetch d.user where d.user.id = :userId")
    Optional<DriverProfile> findDetailedByUserId(@Param("userId") UUID userId);

    @Query("select d from DriverProfile d join fetch d.user order by d.verified asc, d.createdAt desc")
    List<DriverProfile> findAllWithUser();

    boolean existsByUserId(UUID userId);

    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);

    long countByVerifiedFalse();

    long countByAvailabilityAndVerifiedTrue(Availability availability);
}
