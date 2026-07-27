package carelog.carelog.auth.app.port.oauth;

/** Callback 완료 입력이다. code verifier와 redirect URI는 클라이언트에서 받지 않는다. */
public record OAuthCallbackCommand(
        String provider,
        String authorizationCode,
        String state
) {
}
