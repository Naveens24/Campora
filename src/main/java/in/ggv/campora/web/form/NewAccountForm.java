package in.ggv.campora.web.form;

import in.ggv.campora.identity.domain.RoleName;
import jakarta.validation.constraints.NotNull;

/** An account an admin opens for someone, e.g. a driver without an email address. */
public class NewAccountForm extends AccountForm {

    @NotNull(message = "Choose Student or Driver")
    private RoleName role = RoleName.DRIVER;

    public RoleName getRole() {
        return role;
    }

    public void setRole(RoleName role) {
        this.role = role;
    }
}
