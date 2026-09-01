package carelog.carelog.auth.web.internal;

import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ADR-0019 Slice A′가 승인한 단일 내부 endpoint:
 * {@code GET /internal/identity/claims/{accountId}} (servlet-relative).
 *
 * <p>인증/인가는 이 controller가 아니라 {@link InternalIdentitySecurityConfiguration}의
 * 전용 {@code SecurityFilterChain}이 담당한다. 이 계층은 이미 존재하는 CRM projection
 * 소유권({@link CRMIdentityProjectionPort})에 위임할 뿐, 새 projection 경로를 만들지 않는다.
 *
 * <p>{@code enabled=false}일 때는 이 controller 자체가 Bean으로 등록되지 않는다 — 일반
 * {@code SecurityConfig} chain의 {@code /internal/**} denyAll에 기대는 이중 방어가 아니라,
 * "no controller boundary"를 문자 그대로 보장한다.
 */
@Hidden
@RestController
@RequestMapping("/internal/identity/claims")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "carelog.internal.identity-claims",
        name = "enabled",
        havingValue = "true"
)
public class InternalIdentityClaimsController {

    private final CRMIdentityProjectionPort crmIdentityProjectionPort;

    @GetMapping("/{accountId}")
    public ResponseEntity<IdentityClaimsResponse> getClaims(@PathVariable UUID accountId) {
        return crmIdentityProjectionPort.findIdentityClaims(accountId)
                .map(claims -> ResponseEntity.ok(toResponse(claims)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private IdentityClaimsResponse toResponse(CRMIdentityClaims claims) {
        return new IdentityClaimsResponse(
                claims.organizationId(),
                claims.role(),
                claims.publicId());
    }
}
