package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthRedirectUriResolverTest {

    @Test
    void provider와_channel에_설정된_URI만_반환한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oauth.redirect-uris.neutral.WEB", "https://app.example.com/callback")
                .withProperty("oauth.redirect-uris.neutral.MOBILE", "carelog://oauth/callback");
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(environment);

        assertThat(resolver.resolve("NEUTRAL", ClientChannel.WEB))
                .isEqualTo(URI.create("https://app.example.com/callback"));
        assertThat(resolver.resolve("neutral", ClientChannel.MOBILE))
                .isEqualTo(URI.create("carelog://oauth/callback"));
    }

    @Test
    void 미설정_channel은_명시적으로_거부한다() {
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(new MockEnvironment());

        assertThatThrownBy(() -> resolver.resolve("neutral", ClientChannel.WEB))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
    }
}
