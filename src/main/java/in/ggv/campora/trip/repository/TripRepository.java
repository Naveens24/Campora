package in.ggv.campora.trip.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.trip.domain.Trip;
import in.ggv.campora.trip.domain.TripStatus;
import jakarta.persistence.LockModeType;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("""
            select t from Trip t
              join fetch t.pickup
              join fetch t.destination
             where t.driver.id = :driverId
               and t.status in :statuses
             order by t.createdAt desc
            """)
    List<Trip> findByDriverAndStatuses(@Param("driverId") UUID driverId,
                                       @Param("statuses") Collection<TripStatus> statuses);

    @Query("""
            select t from Trip t
              join fetch t.pickup
              join fetch t.destination
             where t.driver.id = :driverId
             order by t.createdAt desc
            """)
    List<Trip> findDriverHistory(@Param("driverId") UUID driverId, Pageable page);

    boolean existsByDriverIdAndStatusIn(UUID driverId, Collection<TripStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);

    Optional<Trip> findByCompanionRideId(UUID companionRideId);

    long countByStatusIn(Collection<TripStatus> statuses);

    long countByStatusAndEndTimeAfter(TripStatus status, Instant after);
}
