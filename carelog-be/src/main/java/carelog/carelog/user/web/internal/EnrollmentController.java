package carelog.carelog.user.web.internal;

import carelog.carelog.user.app.projection.ExternalManagerProjectionService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/internal/v1/identity/carelog/enrollments")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "carelog.identity.enrollment", name = "enabled", havingValue = "true")
public class EnrollmentController {
    private final ExternalManagerProjectionService projection;

    @PutMapping("/{accountId}")
    public ResponseEntity<Void> enroll(@PathVariable UUID accountId, @Valid @RequestBody Request request) {
        projection.project(accountId, request.userId(), request.email(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    public record Request(@NotBlank @Size(max = 255) String userId,
                          @NotBlank @Email @Size(max = 255) String email,
                          @NotBlank @Size(max = 255) String name) {}
}
