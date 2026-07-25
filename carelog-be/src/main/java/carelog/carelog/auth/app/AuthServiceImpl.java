package carelog.carelog.auth.app;


import carelog.carelog.auth.domain.*;
import carelog.carelog.auth.web.dto.request.*;
import carelog.carelog.auth.web.dto.response.*;
import carelog.carelog.common.web.exception.*;
import carelog.carelog.user.domain.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.time.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RedisBlacklistService redisBlacklistService;
    private final Clock clock;


    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            // 1. 인증
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.userId(),
                                    request.password()
                            )
                    );

            // CustomDetails에서 organizationId, publicId 추출
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            // 2. 토큰 생성
            String accessToken = jwtTokenProvider.generateAccessToken(
                    userDetails.getUsername(),
                    userDetails.getOrganizationId(),
                    userDetails.getRole(),
                    userDetails.getPublicId()
            );
            String refreshToken =
                    jwtTokenProvider.generateRefreshToken(userDetails.getUsername());

            // 3. Refresh Token 저장
            refreshTokenRepository.deleteByUserId(userDetails.getUsername());
            refreshTokenRepository.save(RefreshToken.builder()
                    .userId(userDetails.getUsername())
                    .refreshToken(refreshToken)
                    .tokenExpiresAt(jwtTokenProvider.getRefreshTokenExpiryDate())
                    .build()
            );

            return new LoginResponse(accessToken, refreshToken);
        } catch (AuthenticationException e) {
            log.warn("로그인 실패 - userId: {}", request.userId());
            throw new CustomException(ExceptionStatus.INVALID_CREDENTIALS);
        }
    }

    @Override
    @Transactional
    public void logout(String userId, String accessToken) {
        Duration ttl = jwtTokenProvider.getRemainingValidity(accessToken);
        redisBlacklistService.addToBlacklist(accessToken, ttl);

        // Refresh Token DB 삭제
        refreshTokenRepository.deleteByUserId(userId);
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

        // 3. DB 확인
        RefreshToken savedToken = refreshTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new CustomException(ExceptionStatus.REFRESH_TOKEN_NOT_FOUND));

        // 4. DB 기준 만료 여부 검증
        if (savedToken.isExpired(OffsetDateTime.now(clock))) {
            refreshTokenRepository.delete(savedToken);
            throw new CustomException(ExceptionStatus.REFRESH_TOKEN_EXPIRED);
        }

        // 5. userId 일치 여부 검증
        if (!savedToken.getUserId().equals(userId)) {
            throw new CustomException(ExceptionStatus.INVALID_REFRESH_TOKEN);
        }

        // Rotation: User 재조회로 최신 클레임 보장
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        // 6. 새 토큰 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(),
                user.getOrganizationId(),
                user.getRole().name(),
                user.getPublicId()
        );
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        savedToken.updateToken(newRefreshToken, jwtTokenProvider.getRefreshTokenExpiryDate());

        return new TokenRefreshResponse(newAccessToken, newRefreshToken);
    }
}
