package in.ggv.campora.admin.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import in.ggv.campora.admin.domain.AdminAction;

public interface AdminActionRepository extends JpaRepository<AdminAction, UUID> {

    Page<AdminAction> findByTargetIdOrderByCreatedAtDesc(UUID targetId, Pageable page);

    Page<AdminAction> findAllByOrderByCreatedAtDesc(Pageable page);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update AdminAction a set a.targetName = 'Deleted user', a.targetId = null, a.detail = null where a.targetId = :id")
    void forgetTarget(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("update AdminAction a set a.adminName = 'Deleted user' where a.adminId = :id")
    void forgetAdmin(@Param("id") UUID id);
}
