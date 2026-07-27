package carelog.carelog.auth.app.adapter.oauth.kakao;

import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthProviderException;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Kakao 응답 body를 노출하지 않고 Provider-neutral 실패로 바꾼다. */
@Component
@Conditional(KakaoOAuthConfiguredCondition.class)
public class KakaoOAuthErrorMapper {

    public OAuthProviderException tokenFailure(int status) {
        return new OAuthProviderException(status == 429 || status >= 500
                ? OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE
                : OAuthLoginResult.FailureReason.CODE_EXCHANGE_FAILED);
    }

    public OAuthProviderException userInfoFailure(int status) {
        return new OAuthProviderException(status == 429 || status >= 500
                ? OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE
                : OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED);
    }

    public OAuthProviderException unavailable(Throwable cause) {
        return new OAuthProviderException(OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE, cause);
    }

    public OAuthProviderException codeExchangeFailure(Throwable cause) {
        return new OAuthProviderException(OAuthLoginResult.FailureReason.CODE_EXCHANGE_FAILED, cause);
    }

    public OAuthProviderException principalUnverified(Throwable cause) {
        return new OAuthProviderException(OAuthLoginResult.FailureReason.PRINCIPAL_UNVERIFIED, cause);
    }
}
