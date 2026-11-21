package carelog.carelog.user.web.internal;

import carelog.carelog.user.app.projection.ExternalManagerProjectionService.ProjectionConflictException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = EnrollmentController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "carelog.identity.enrollment", name = "enabled", havingValue = "true")
public class EnrollmentExceptionHandler {
    @ExceptionHandler(ProjectionConflictException.class)
    ResponseEntity<Void> conflict(Exception ignored) { return ResponseEntity.status(409).build(); }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Void> malformed(Exception ignored) { return ResponseEntity.badRequest().build(); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Void> failure(Exception ignored) { return ResponseEntity.internalServerError().build(); }
}
