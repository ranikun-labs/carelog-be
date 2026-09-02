package carelog.carelog.auth.web.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 내부 경계에서 malformed accountId(400)와 그 외 런타임/영속화 실패(500)를 구분한다. */
@RestControllerAdvice(assignableTypes = InternalIdentityClaimsController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
        prefix = "carelog.internal.identity-claims",
        name = "enabled",
        havingValue = "true"
)
public class InternalIdentityProjectionExceptionHandler {

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Void> handleMalformedRequest(Exception ignored) {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Void> handleRuntimeFailure(Exception ignored) {
        return ResponseEntity.internalServerError().build();
    }
}
