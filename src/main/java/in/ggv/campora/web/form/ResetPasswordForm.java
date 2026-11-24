package in.ggv.campora.web.form;

import jakarta.validation.constraints.NotBlank;

public class ResetPasswordForm extends NewPasswordForm {

    @NotBlank(message = "Enter the code from your email")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
