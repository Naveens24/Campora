package in.ggv.campora.companion.repository;

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

import in.ggv.campora.companion.domain.CompanionRide;
import in.ggv.campora.companion.domain.RideStatus;
import jakarta.persistence.LockModeType;

public interface CompanionRideRepository extends JpaRepository<CompanionRide, UUID> {

    /** Joinable rides offered by other students, soonest departure first. */
    @Query("""
            select r from CompanionRide r
              join fetch r.pickup
              join fetch r.destination
              join fetch r.host h
              join fetch h.user
             where r.status = :status
               and r.departureTime > :after
               and h.id <> :excludeHostId
             order by r.departureTime asc
            """)
    List<CompanionRide> findJoinable(@Param("status") RideStatus status,
                                     @Param("after") Instant after,
                                     @Param("excludeHostId") UUID excludeHostId);

    @Query("""
            select r from CompanionRide r
              join fetch r.pickup
              join fetch r.destination
             where r.host.id = :hostId
               and r.status in :statuses
             order by r.departureTime desc
            """)
    List<CompanionRide> findByHostAndStatuses(@Param("hostId") UUID hostId,
                                              @Param("statuses") Collection<RideStatus> statuses);

    @Query("""
            select r from CompanionRide r
              join fetch r.pickup
              join fetch r.destination
             where r.host.id = :hostId
             order by r.departureTime desc
            """)
    List<CompanionRide> findHostHistory(@Param("hostId") UUID hostId, Pageable page);

    boolean existsByHostIdAndStatusIn(UUID hostId, Collection<RideStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CompanionRide r where r.id = :id")
    Optional<CompanionRide> findByIdForUpdate(@Param("id") UUID id);

    long countByStatusIn(Collection<RideStatus> statuses);
}
