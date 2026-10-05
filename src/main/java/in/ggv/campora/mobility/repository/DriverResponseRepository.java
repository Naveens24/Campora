package in.ggv.campora.mobility.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.mobility.domain.DriverResponse;
import in.ggv.campora.mobility.domain.ResponseStatus;

public interface DriverResponseRepository extends JpaRepository<DriverResponse, UUID> {

    /** Passengers of a trip — identities are only ever loaded after acceptance. */
    @Query("""
            select dr from DriverResponse dr
              join fetch dr.request r
              join fetch r.student s
              join fetch s.user
             where dr.trip.id = :tripId
               and dr.status = :status
             order by dr.createdAt asc
            """)
    List<DriverResponse> findByTripWithPassengers(@Param("tripId") UUID tripId,
                                                  @Param("status") ResponseStatus status);

    @Query("""
            select dr from DriverResponse dr
              join fetch dr.trip
              join fetch dr.driver d
              join fetch d.user
             where dr.request.id = :requestId
               and dr.status = :status
            """)
    Optional<DriverResponse> findByRequestWithDriver(@Param("requestId") UUID requestId,
                                                     @Param("status") ResponseStatus status);

    List<DriverResponse> findByTripIdAndStatus(UUID tripId, ResponseStatus status);

    long countByTripIdAndStatus(UUID tripId, ResponseStatus status);

    /** A student's auto rides, newest first, for the history page. */
    @Query("""
            select dr from DriverResponse dr
              join fetch dr.trip t
              join fetch t.pickup
              join fetch t.destination
              join fetch dr.driver d
              join fetch d.user
             where dr.request.student.id = :studentId
               and dr.status = :status
             order by t.createdAt desc
            """)
    List<DriverResponse> findStudentRides(@Param("studentId") UUID studentId,
                                          @Param("status") ResponseStatus status,
                                          Pageable page);
}
