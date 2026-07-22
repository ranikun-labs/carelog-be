package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link CRMIdentityProjectionPort}의 Legacy 구현.
 *
 * <p>{@link UserRepository} 조회 결과({@link User})에서 organizationId/role/publicId
 * 세 필드만 뽑아 {@link CRMIdentityClaims}로 변환한다. {@link User} Entity 자체는
 * Port 반환 타입으로 노출하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class LegacyCrmIdentityProjectionAdapter implements CRMIdentityProjectionPort {

    private final UserRepository userRepository;

    @Override
    public CRMIdentityClaims getIdentityClaims(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        return new CRMIdentityClaims(user.getOrganizationId(), user.getRole().name(), user.getPublicId());
    }
}
