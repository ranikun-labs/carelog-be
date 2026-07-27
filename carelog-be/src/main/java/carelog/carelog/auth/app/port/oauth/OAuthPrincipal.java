package carelog.carelog.auth.app.port.oauth;

/** Provider SDK 응답을 노출하지 않는 검증된 외부 신원이다. */
public record OAuthPrincipal(
        String provider,
        String providerSubject,
        String email,
        boolean emailVerified,
        String displayName
) {
}
