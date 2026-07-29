package carelog.carelog.auth.app.port.oauth;

/** Authorization 시작 입력이다. redirect URI 원문은 입력으로 받지 않는다. */
public record OAuthAuthorizationCommand(
        String provider,
        ClientChannel clientChannel,
        String returnTo,
        String clientId
) {

    /** 기존 Public API는 clientId 없이 WEB 또는 MOBILE 채널별 Carelog 기본 Client로 호환한다. */
    public OAuthAuthorizationCommand(String provider, ClientChannel clientChannel, String returnTo) {
        this(provider, clientChannel, returnTo, null);
    }
}
