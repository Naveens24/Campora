package in.ggv.campora.common.exception;

/** The requested record does not exist, or the current user may not see it. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what) {
        super(what + " not found");
    }
}
