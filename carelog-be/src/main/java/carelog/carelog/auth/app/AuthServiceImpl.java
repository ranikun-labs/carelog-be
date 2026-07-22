package carelog.carelog.auth.app;


import carelog.carelog.auth.app.port.*;
import carelog.carelog.auth.web.dto.request.*;
import carelog.carelog.auth.web.dto.response.*;
import carelog.carelog.common.web.exception.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.time.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {
    // Identity 경계 Port에만 의존한다. 기존 CRM 직접 결합(인증/조회/세션)은 Adapter 뒤로 숨겼다.
    private final CredentialPort credentialPort;
    private final CRMIdentityProjectionPort crmIdentityProjectionPort;
    private final TokenSessionPort tokenSessionPort;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisBlacklistService redisBlacklistService;
    private final Clock clock;


    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. 인증 (인증 실패는 Port가 기존과 동일하게 INVALID_CREDENTIALS로 매핑)
        UserPrincipal principal = credentialPort.authenticate(request.userId(), request.password());

        // 2. 토큰 생성 (organizationId/role/publicId claim 의미 불변)
        String accessToken = jwtTokenProvider.generateAccessToken(
                principal.getUserId(),
                principal.getOrganizationId(),
                principal.getRole(),
                principal.getPublicId()
        );
        String refreshToken =
                jwtTokenProvider.generateRefreshToken(principal.getUserId());

        // 3. Refresh Session 교체 (기존 delete → save 순서 보존)
        tokenSessionPort.replaceForUser(
                principal.getUserId(),
                refreshToken,
                jwtTokenProvider.getRefreshTokenExpiryDate()
        );

        return new LoginResponse(accessToken, refreshToken);
    }

    @Override
    @Transactional
    public void logout(String userId, String accessToken) {
        Duration ttl = jwtTokenProvider.getRemainingValidity(accessToken);
        redisBlacklistService.addToBlacklist(accessToken, ttl);

        // Refresh Session 삭제 (blacklist → delete 순서 보존)
        tokenSessionPort.deleteForUser(userId);
        log.info("로그아웃: {}", userId);
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();

        // 1. 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ExceptionStatus.INVALID_REFRESH_TOKEN);
        }

        // 2. userId 추출
        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 3. Session 조회 (조회 키는 요청으로 받은 기존 raw refresh token)
        RefreshSession session = tokenSessionPort.findByToken(refreshToken)
                .orElseThrow(() -> new CustomException(ExceptionStatus.REFRESH_TOKEN_NOT_FOUND));

        // 4. 만료 여부 검증 (만료 시 Session 삭제 후 예외 — 기존 삭제 순서 보존)
        if (OffsetDateTime.now(clock).isAfter(session.expiresAt())) {
            tokenSessionPort.delete(session);
            throw new CustomException(ExceptionStatus.REFRESH_TOKEN_EXPIRED);
        }

        // 5. userId 일치 여부 검증
        if (!session.userId().equals(userId)) {
            throw new CustomException(ExceptionStatus.INVALID_REFRESH_TOKEN);
        }

        // Rotation: 최신 CRM claim 재조회로 최신 클레임 보장
        CRMIdentityClaims claims = crmIdentityProjectionPort.getIdentityClaims(userId);

        // 6. 새 토큰 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                userId,
                claims.organizationId(),
                claims.role(),
                claims.publicId()
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        // 7. Session 회전 (findByToken이 반환한 기존 Session을 그대로 전달, dirty checking 보존)
        tokenSessionPort.rotate(session, newRefreshToken, jwtTokenProvider.getRefreshTokenExpiryDate());

        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }
}
