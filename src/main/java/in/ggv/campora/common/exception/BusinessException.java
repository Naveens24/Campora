package in.ggv.campora.common.exception;

/**
 * A rule of the domain was violated (e.g. "hotspot already closed").
 * The message is safe to show to the user as-is.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
