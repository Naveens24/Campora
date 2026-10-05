package in.ggv.campora.campus.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import in.ggv.campora.campus.domain.Destination;

public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    List<Destination> findByActiveTrueOrderByTypeAscSortOrderAscNameAsc();

    List<Destination> findAllByOrderByTypeAscSortOrderAscNameAsc();

    Optional<Destination> findByIdAndActiveTrue(UUID id);

    Optional<Destination> findByName(String name);

    boolean existsByNameIgnoreCase(String name);
}
