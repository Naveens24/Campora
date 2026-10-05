package in.ggv.campora.common.web;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.support.RequestContextUtils;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns domain errors on HTML pages into a friendly flash message on the
 * page the user came from (Post/Redirect/Get), instead of an error screen.
 */
@ControllerAdvice(annotations = Controller.class)
@Order(Ordered.LOWEST_PRECEDENCE)
public class WebExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionAdvice.class);

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, HttpServletRequest request) {
        return flashAndGoBack(request, e.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public String concurrent(RuntimeException e, HttpServletRequest request) {
        log.info("Concurrent update rejected: {}", e.getMessage());
        return flashAndGoBack(request, "Someone else just changed this. Please check and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Integrity violation: {}", e.getMostSpecificCause().getMessage());
        return flashAndGoBack(request, "That conflicts with existing data. Please refresh and try again.");
    }

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound() {
        return "error/404";
    }

    private static String flashAndGoBack(HttpServletRequest request, String message) {
        RequestContextUtils.getOutputFlashMap(request).put(Flash.ERROR, message);
        return "redirect:" + safeReferer(request);
    }

    /** Same-site path of the Referer header, or /dashboard. Never an open redirect. */
    static String safeReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                boolean sameHost = uri.getHost() == null || uri.getHost().equalsIgnoreCase(request.getServerName());
                String path = uri.getRawPath();
                if (sameHost && path != null && path.startsWith("/") && !path.startsWith("//")) {
                    String contextPath = request.getContextPath();
                    if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
                        path = path.substring(contextPath.length());
                    }
                    return path.isEmpty() ? "/dashboard" : path;
                }
            } catch (IllegalArgumentException ignored) {
                // malformed header — fall through
            }
        }
        return "/dashboard";
    }
}
