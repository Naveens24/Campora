package in.ggv.campora.companion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.companion.domain.CompanionRideMember;
import in.ggv.campora.companion.domain.MemberStatus;
import in.ggv.campora.companion.domain.RideStatus;

public interface CompanionRideMemberRepository extends JpaRepository<CompanionRideMember, UUID> {

    @Query("""
            select m from CompanionRideMember m
              join fetch m.student s
              join fetch s.user
             where m.ride.id = :rideId
               and m.status = :status
             order by m.createdAt asc
            """)
    List<CompanionRideMember> findByRideWithStudents(@Param("rideId") UUID rideId,
                                                     @Param("status") MemberStatus status);

    /** The ride a student is currently riding in (as a passenger), if any. */
    @Query("""
            select m from CompanionRideMember m
              join fetch m.ride r
              join fetch r.pickup
              join fetch r.destination
              join fetch r.host h
              join fetch h.user
             where m.student.id = :studentId
               and m.status = :memberStatus
               and r.status in :rideStatuses
            """)
    List<CompanionRideMember> findActiveForStudent(@Param("studentId") UUID studentId,
                                                   @Param("memberStatus") MemberStatus memberStatus,
                                                   @Param("rideStatuses") Collection<RideStatus> rideStatuses);

    @Query("""
            select m from CompanionRideMember m
              join fetch m.ride r
              join fetch r.pickup
              join fetch r.destination
              join fetch r.host h
              join fetch h.user
             where m.student.id = :studentId
               and m.status = :memberStatus
             order by r.departureTime desc
            """)
    List<CompanionRideMember> findStudentHistory(@Param("studentId") UUID studentId,
                                                 @Param("memberStatus") MemberStatus memberStatus,
                                                 Pageable page);

    Optional<CompanionRideMember> findByTravelRequestIdAndStatus(UUID travelRequestId, MemberStatus status);

    Optional<CompanionRideMember> findByRideIdAndStudentIdAndStatus(UUID rideId, UUID studentId, MemberStatus status);

    List<CompanionRideMember> findByRideIdAndStatus(UUID rideId, MemberStatus status);
}
