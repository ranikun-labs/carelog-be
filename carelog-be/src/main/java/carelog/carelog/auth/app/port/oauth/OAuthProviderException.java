package carelog.carelog.auth.app.port.oauth;

/** Provider 인증 실패만 OAuth 결과로 변환하기 위한 전용 예외다. */
public class OAuthProviderException extends RuntimeException {

    private final OAuthLoginResult.FailureReason reason;

    public OAuthProviderException(OAuthLoginResult.FailureReason reason) {
        this.reason = reason;
    }

    public OAuthProviderException(OAuthLoginResult.FailureReason reason, Throwable cause) {
        super(cause);
        this.reason = reason;
    }

    public OAuthLoginResult.FailureReason reason() {
        return reason;
    }
}
