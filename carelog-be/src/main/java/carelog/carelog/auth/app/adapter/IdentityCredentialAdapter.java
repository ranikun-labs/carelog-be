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
 * organizationId/role/publicId는 {@link CRMIdentityProjectionPort}로 조회하되, 조회 키는 Identity
 * Foundation B0부터 loginId가 아니라 {@code PasswordCredential.accountId}다(기존 Token Claim 값 자체는 동일).
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
        Optional<PasswordCredential> credentialOpt = passwordCredentialRepository.findByLoginId(userId);

        if (credentialOpt.isEmpty() || !passwordEncoder.matches(password, credentialOpt.get().getPasswordHash())) {
            // 로그인 실패 운영 로그: userId만 남기고 비밀번호/토큰은 남기지 않는다.
            log.warn("로그인 실패 - userId: {}", userId);
            throw new CustomException(ExceptionStatus.INVALID_CREDENTIALS);
        }

        PasswordCredential credential = credentialOpt.get();
        CRMIdentityClaims claims = crmIdentityProjectionPort.getIdentityClaims(credential.getAccountId());
        return new IdentityAuthenticatedPrincipal(
                credential.getAccountId(), userId, claims.organizationId(), claims.role(), claims.publicId());
    }
}
