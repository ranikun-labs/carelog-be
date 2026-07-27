package carelog.carelog.auth.app.port;

/**
 * 로그인 성공 후 함께 발급되는 Access/Refresh Token 묶음이다.
 */
public record AuthTokenBundle(
        String accessToken,
        String refreshToken
) {
}
