package in.ggv.campora.campus.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import in.ggv.campora.campus.domain.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();

    Optional<Department> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);
}
