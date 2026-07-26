package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * {@link CRMIdentityProjectionPort}의 Legacy 구현.
 *
 * <p>{@link UserRepository} 조회 결과({@link User})에서 organizationId/role/publicId
 * 세 필드만 뽑아 {@link CRMIdentityClaims}로 변환한다. {@link User} Entity 자체는
 * Port 반환 타입으로 노출하지 않는다.
 *
 * <p>{@code users.account_id}로 직접 조회한다 — 현재 MANAGER는 accountId==publicId가 성립하지만,
 * 그 사실에 기대어 {@code findByPublicId}를 재사용하지 않고 accountId 전용 조회를 사용한다.
 */
@Component
@RequiredArgsConstructor
public class LegacyCrmIdentityProjectionAdapter implements CRMIdentityProjectionPort {

    private final UserRepository userRepository;

    @Override
    public CRMIdentityClaims getIdentityClaims(UUID accountId) {
        User user = userRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        return new CRMIdentityClaims(user.getOrganizationId(), user.getRole().name(), user.getPublicId());
    }
}
