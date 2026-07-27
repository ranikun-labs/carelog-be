package carelog.carelog.auth.app.port.oauth;

/** Authorization 시작 입력이다. redirect URI 원문은 입력으로 받지 않는다. */
public record OAuthAuthorizationCommand(
        String provider,
        ClientChannel clientChannel,
        String returnTo
) {
}
