package carelog.carelog.auth.app;

import carelog.carelog.auth.app.port.AuthTokenBundle;
import carelog.carelog.auth.app.port.CRMIdentityClaims;
import carelog.carelog.auth.app.port.TokenSessionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Password와 OAuth 로그인 경로가 공유하는 토큰 발급 유스케이스다.
 *
 * <p>claim 조회는 호출자가 담당한다. 따라서 claim 부재와 토큰/세션 인프라 실패를 서로 다른
 * 제어 흐름으로 다룰 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthTokenIssuanceService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenSessionPort tokenSessionPort;

    public AuthTokenBundle issue(UUID accountId, CRMIdentityClaims claims) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                accountId,
                claims.organizationId(),
                claims.role(),
                claims.publicId()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(accountId);

        tokenSessionPort.replaceForAccount(
                accountId,
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpiryDate()
        );

        return new AuthTokenBundle(accessToken, refreshToken);
    }

    public AuthTokenBundle issueForPrincipal(UserPrincipal principal) {
        return issue(
                principal.getAccountId(),
                new CRMIdentityClaims(
                        principal.getOrganizationId(),
                        principal.getRole(),
                        principal.getPublicId()
                )
        );
    }
}
