package in.ggv.campora.mobility.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.mobility.domain.RequestStatus;
import in.ggv.campora.mobility.domain.RequestType;
import in.ggv.campora.mobility.domain.TravelRequest;
import in.ggv.campora.mobility.dto.HotspotView;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface TravelRequestRepository extends JpaRepository<TravelRequest, UUID> {

    /**
     * The demand engine's read side: open requests grouped into hotspots,
     * biggest demand first, then longest waiting. Served by ix_request_cluster.
     */
    @Query("""
            select new in.ggv.campora.mobility.dto.HotspotView(
                       p.id, p.name, d.id, d.name,
                       count(r), sum(r.seatsRequired), min(r.createdAt))
              from TravelRequest r
              join r.pickup p
              join r.destination d
             where r.status = :status
               and r.requestType <> :excludedType
               and r.expiresAt > :now
             group by p.id, p.name, d.id, d.name
             order by sum(r.seatsRequired) desc, min(r.createdAt) asc
            """)
    List<HotspotView> findHotspots(@Param("status") RequestStatus status,
                                   @Param("excludedType") RequestType excludedType,
                                   @Param("now") Instant now);

    /**
     * The demand engine's write side: lock every open request on a route,
     * oldest first. Concurrent drivers queue here, so two drivers can never
     * be handed the same request.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
            select r from TravelRequest r
             where r.pickup.id = :pickupId
               and r.destination.id = :destinationId
               and r.status = :status
               and r.requestType <> :excludedType
               and r.expiresAt > :now
             order by r.createdAt asc
            """)
    List<TravelRequest> lockOpenForRoute(@Param("pickupId") UUID pickupId,
                                         @Param("destinationId") UUID destinationId,
                                         @Param("status") RequestStatus status,
                                         @Param("excludedType") RequestType excludedType,
                                         @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TravelRequest r where r.id = :id")
    Optional<TravelRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select r from TravelRequest r
              join fetch r.pickup
              join fetch r.destination
             where r.student.id = :studentId
               and r.status in :statuses
            """)
    List<TravelRequest> findByStudentAndStatuses(@Param("studentId") UUID studentId,
                                                 @Param("statuses") Collection<RequestStatus> statuses);

    @Query("""
            select r from TravelRequest r
              join fetch r.pickup
              join fetch r.destination
             where r.student.id = :studentId
             order by r.createdAt desc
            """)
    List<TravelRequest> findHistory(@Param("studentId") UUID studentId, Pageable page);

    boolean existsByStudentIdAndStatusIn(UUID studentId, Collection<RequestStatus> statuses);

    @Query("""
            select count(r) from TravelRequest r
             where r.pickup.id = :pickupId
               and r.destination.id = :destinationId
               and r.status = :status
               and r.expiresAt > :now
            """)
    long countOnRoute(@Param("pickupId") UUID pickupId,
                      @Param("destinationId") UUID destinationId,
                      @Param("status") RequestStatus status,
                      @Param("now") Instant now);

    /** Bulk-expire requests nobody served in time (one UPDATE, no entity loading). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TravelRequest r
               set r.status = :expired,
                   r.updatedAt = :now,
                   r.version = r.version + 1
             where r.status = :open
               and r.expiresAt <= :now
            """)
    int expireStale(@Param("open") RequestStatus open,
                    @Param("expired") RequestStatus expired,
                    @Param("now") Instant now);

    long countByStatus(RequestStatus status);
}
