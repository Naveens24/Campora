package in.ggv.campora.admin.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import in.ggv.campora.admin.service.AdminService;

/** The "waiting for review" badge shown in the admin menu. */
@ControllerAdvice(basePackageClasses = AdminController.class)
public class AdminModelAdvice {

    private final AdminService admin;

    public AdminModelAdvice(AdminService admin) {
        this.admin = admin;
    }

    @ModelAttribute("pendingSuggestions")
    public long pendingSuggestions() {
        return admin.pendingSuggestions();
    }
}
