package in.ggv.campora.admin.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import in.ggv.campora.common.domain.BaseEntity;
import in.ggv.campora.identity.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One thing an admin did. {@code summary} never holds personal details;
 * free text goes to {@code detail}, which is cleared when the account is deleted.
 */
@Entity
@Table(name = "admin_action")
@SQLRestriction("deleted = false")
public class AdminAction extends BaseEntity {

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "admin_name", nullable = false, length = 130)
    private String adminName;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "target_name", length = 130)
    private String targetName;

    @Column(name = "summary", nullable = false, length = 160)
    private String summary;

    @Column(name = "detail", length = 300)
    private String detail;

    protected AdminAction() {
    }

    public AdminAction(User admin, User target, String summary, String detail) {
        this.adminId = admin.getId();
        this.adminName = admin.getFullName();
        if (target != null) {
            this.targetId = target.getId();
            this.targetName = target.getFullName();
        }
        this.summary = summary;
        this.detail = detail;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getAdminName() {
        return adminName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetail() {
        return detail;
    }
}
