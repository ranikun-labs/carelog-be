package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.port.RefreshSession;
import carelog.carelog.auth.app.port.TokenSessionPort;
import carelog.carelog.auth.domain.RefreshToken;
import carelog.carelog.auth.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * {@link TokenSessionPort}의 Legacy 구현.
 *
 * <p>{@link RefreshTokenRepository}/{@link RefreshToken} Entity에 위임하며, 현재 저장 포맷(평문)과
 * delete→save(로그인)/dirty-checking 기반 rotate(refresh) 순서를 그대로 승계한다.
 * 만료 검사·JWT subject 검사는 Auth 도메인 규칙이므로 이 Adapter로 옮기지 않는다.
 *
 * <p>{@link RefreshSession}은 Entity 식별자를 담지 않으므로, rotate/delete는 Port에 유일하게 노출된
 * 자연 키인 {@code tokenValue}로 Entity를 다시 조회해 JPA dirty-checking/삭제를 수행한다.
 */
@Component
@RequiredArgsConstructor
public class LegacyTokenSessionAdapter implements TokenSessionPort {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public void replaceForUser(String userId, String newToken, OffsetDateTime expiresAt) {
        refreshTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .refreshToken(newToken)
                .tokenExpiresAt(expiresAt)
                .build());
    }

    @Override
    public Optional<RefreshSession> findByToken(String rawToken) {
        return refreshTokenRepository.findByRefreshToken(rawToken).map(this::toSession);
    }

    @Override
    public void rotate(RefreshSession session, String newToken, OffsetDateTime newExpiresAt) {
        RefreshToken entity = refreshTokenRepository.findByRefreshToken(session.tokenValue())
                .orElseThrow(() -> new IllegalStateException(
                        "rotate 대상 RefreshToken을 찾을 수 없습니다: " + session.tokenValue()));
        entity.updateToken(newToken, newExpiresAt);
    }

    @Override
    public void deleteForUser(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Override
    public void delete(RefreshSession session) {
        refreshTokenRepository.findByRefreshToken(session.tokenValue())
                .ifPresent(refreshTokenRepository::delete);
    }

    private RefreshSession toSession(RefreshToken entity) {
        return new RefreshSession(entity.getRefreshToken(), entity.getUserId(), entity.getTokenExpiresAt());
    }
}
