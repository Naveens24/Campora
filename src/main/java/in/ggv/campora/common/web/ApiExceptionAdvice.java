package in.ggv.campora.common.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import in.ggv.campora.common.exception.BusinessException;
import in.ggv.campora.common.exception.NotFoundException;

/** RFC 9457 problem responses for the JSON API (/api/v1/**). */
@RestControllerAdvice(basePackages = "in.ggv.campora.web.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail business(BusinessException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Concurrent update — please retry.");
    }
}
