package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.CRMIdentityProjectionPort;
import carelog.carelog.auth.app.port.CredentialPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link CredentialPort}의 Identity Foundation 구현.
 *
 * <p>비밀번호 검증을 {@code password_credentials}(Identity 소유)에서 수행한다.
 * organizationId/role/publicId는 여전히 {@link CRMIdentityProjectionPort}(변경 없음)로 조회해
 * 기존 Token Claim과 완전히 동일한 값을 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityCredentialAdapter implements CredentialPort {

    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final CRMIdentityProjectionPort crmIdentityProjectionPort;

    @Override
    public UserPrincipal authenticate(String userId, String password) {
        Optional<PasswordCredential> credential = passwordCredentialRepository.findByLoginId(userId);

        if (credential.isEmpty() || !passwordEncoder.matches(password, credential.get().getPasswordHash())) {
            // 로그인 실패 운영 로그: userId만 남기고 비밀번호/토큰은 남기지 않는다.
            log.warn("로그인 실패 - userId: {}", userId);
            throw new CustomException(ExceptionStatus.INVALID_CREDENTIALS);
        }

        CRMIdentityClaims claims = crmIdentityProjectionPort.getIdentityClaims(userId);
        return new IdentityAuthenticatedPrincipal(
                userId, claims.organizationId(), claims.role(), claims.publicId());
    }
}
