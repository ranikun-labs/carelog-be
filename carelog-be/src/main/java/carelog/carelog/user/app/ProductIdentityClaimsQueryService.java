package carelog.carelog.user.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Carelog Product User에서 Identity가 소비할 최소 claims snapshot만 조회한다.
 *
 * <p>이 서비스는 Product-owned {@code UserRepository}를 application boundary 뒤에 감추고,
 * User Entity 또는 CRM 개인정보를 transport 계층으로 전달하지 않는다.
 */
@Service
@Transactional(readOnly = true)
@ConditionalOnProperty(
        prefix = "carelog.internal.identity-claims",
        name = "enabled",
        havingValue = "true"
)
public class ProductIdentityClaimsQueryService {

    private final UserRepository userRepository;

    public ProductIdentityClaimsQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public ProductIdentityClaims getClaims(UUID accountId) {
        return userRepository.findByAccountId(accountId)
                .map(user -> new ProductIdentityClaims(
                        user.getOrganizationId(),
                        user.getRole().name(),
                        user.getPublicId()
                ))
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
    }

    public record ProductIdentityClaims(
            UUID organizationId,
            String role,
            UUID publicId
    ) {
    }
}
