package in.ggv.campora.common.web;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tiny helpers for Post/Redirect/Get flash messages. */
public final class Flash {

    public static final String SUCCESS = "success";
    public static final String ERROR = "error";

    private Flash() {
    }

    public static void success(RedirectAttributes attrs, String message) {
        attrs.addFlashAttribute(SUCCESS, message);
    }

    public static void error(RedirectAttributes attrs, String message) {
        attrs.addFlashAttribute(ERROR, message);
    }

    /** The first validation message, for forms that redirect instead of re-rendering. */
    public static String firstError(BindingResult result) {
        ObjectError error = result.getAllErrors().get(0);
        String message = error.getDefaultMessage();
        if (error instanceof FieldError fe && (message == null || message.startsWith("must"))) {
            return "Please check " + fe.getField() + ".";
        }
        return message == null ? "Please check the form." : message;
    }
}
