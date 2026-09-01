package carelog.carelog.user.web;

import carelog.carelog.user.app.ProductIdentityClaimsQueryService;
import carelog.carelog.user.web.dto.IdentityClaimsResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Identity가 Carelog Product claims snapshot을 조회하는 private read API.
 *
 * <p>인증은 이 controller가 아니라 enabled 상태에서만 등록되는 전용 SecurityFilterChain이 담당한다.
 * 이 계층은 transport DTO와 application query만 연결한다.
 */
@RestController
@RequestMapping("/internal/v1/identity/accounts")
@ConditionalOnProperty(
        prefix = "carelog.internal.identity-claims",
        name = "enabled",
        havingValue = "true"
)
public class IdentityClaimsController {

    private final ProductIdentityClaimsQueryService claimsQueryService;

    public IdentityClaimsController(ProductIdentityClaimsQueryService claimsQueryService) {
        this.claimsQueryService = claimsQueryService;
    }

    @GetMapping("/{accountId}/claims")
    public IdentityClaimsResponse getClaims(@PathVariable UUID accountId) {
        ProductIdentityClaimsQueryService.ProductIdentityClaims claims =
                claimsQueryService.getClaims(accountId);
        return new IdentityClaimsResponse(
                claims.organizationId(),
                claims.role(),
                claims.publicId()
        );
    }
}
