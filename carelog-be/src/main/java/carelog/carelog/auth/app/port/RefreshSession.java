package carelog.carelog.auth.app.port;

import java.time.OffsetDateTime;

/**
 * Refresh Token 세션의 내부 표현.
 *
 * <p>{@code RefreshToken} Entity를 Port 경계 밖으로 노출하지 않기 위한 최소 모델이다.
 * 현재 저장 필드를 그대로 담는다.
 *
 * <p>{@code tokenValue}는 현재 평문이며, 향후 보안 결정(hash 전환) 시 대체/제거 대상으로 미리 표시해 둔다.
 */
public record RefreshSession(
        String tokenValue,
        String userId,
        OffsetDateTime expiresAt
) {
}
