package in.ggv.campora.campus.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.campus.domain.DestinationRequest;
import in.ggv.campora.campus.domain.DestinationRequestStatus;

public interface DestinationRequestRepository extends JpaRepository<DestinationRequest, UUID> {

    @Query("""
            select r from DestinationRequest r
              join fetch r.student s
              join fetch s.user
             where r.status = :status
             order by r.createdAt asc
            """)
    List<DestinationRequest> findByStatusWithStudent(@Param("status") DestinationRequestStatus status);

    List<DestinationRequest> findTop10ByStudentIdOrderByCreatedAtDesc(UUID studentId);

    long countByStatus(DestinationRequestStatus status);

    boolean existsByStudentIdAndStatus(UUID studentId, DestinationRequestStatus status);
}
