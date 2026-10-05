package in.ggv.campora.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import in.ggv.campora.identity.domain.Role;
import in.ggv.campora.identity.domain.RoleName;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
