package in.ggv.campora.admin.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.ggv.campora.admin.domain.AdminAction;
import in.ggv.campora.admin.repository.AdminActionRepository;
import in.ggv.campora.identity.domain.User;

/** Who did what in the admin panel, and to whom. */
@Service
@Transactional
public class AdminLog {

    private final AdminActionRepository actions;

    public AdminLog(AdminActionRepository actions) {
        this.actions = actions;
    }

    public void record(User admin, User target, String summary, String detail) {
        String text = detail == null || detail.isBlank() ? null : detail.trim();
        actions.save(new AdminAction(admin, target, summary,
                text != null && text.length() > 300 ? text.substring(0, 300) : text));
    }

    public void record(User admin, User target, String summary) {
        record(admin, target, summary, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminAction> recent(int page, int size) {
        return actions.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<AdminAction> about(UUID userId, int size) {
        return actions.findByTargetIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size));
    }

    /** Removes a deleted person's name and any free text about them. */
    public void forget(UUID userId) {
        actions.forgetTarget(userId);
        actions.forgetAdmin(userId);
    }
}
