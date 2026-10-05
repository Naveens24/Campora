package in.ggv.campora.profile.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.profile.domain.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByUserId(UUID userId);

    @Query("""
            select s from StudentProfile s
              join fetch s.user
              join fetch s.department
             where s.user.id = :userId
            """)
    Optional<StudentProfile> findDetailedByUserId(@Param("userId") UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByEnrollmentNoIgnoreCase(String enrollmentNo);
}
