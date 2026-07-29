package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthRedirectUriResolverTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CallbackPropertiesConfiguration.class);

    @Test
    void provider와_clientId_계층의_환경설정을_binding한다() {
        contextRunner.withPropertyValues(
                "oauth.provider-callbacks.kakao.carelog-web=https://web.example.com/callback"
        ).run(context -> assertThat(context.getBean(OAuthProviderCallbackProperties.class)
                .find("kakao", "carelog-web"))
                .isEqualTo("https://web.example.com/callback"));
    }

    @Test
    void provider와_clientId에_설정된_URI를_반환한다() {
        OAuthProviderCallbackProperties properties = properties(Map.of(
                "neutral", Map.of(
                        "carelog-web", "https://web.example.com/callback",
                        "carelog-mobile", "carelog://oauth/callback"
                )
        ));
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(properties, new MockEnvironment());

        assertThat(resolver.resolve("NEUTRAL", client("carelog-web", ProductClientChannel.WEB)))
                .isEqualTo(URI.create("https://web.example.com/callback"));
        assertThat(resolver.resolve("neutral", client("carelog-mobile", ProductClientChannel.MOBILE)))
                .isEqualTo(URI.create("carelog://oauth/callback"));
    }

    @Test
    void 기존_WEB과_MOBILE_channel_설정은_정확한_Carelog_Client에만_호환된다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("oauth.redirect-uris.neutral.WEB", "https://web.example.com/callback")
                .withProperty("oauth.redirect-uris.neutral.MOBILE", "carelog://oauth/callback");
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(new OAuthProviderCallbackProperties(), environment);

        assertThat(resolver.resolve("neutral", client("carelog-web", ProductClientChannel.WEB)))
                .isEqualTo(URI.create("https://web.example.com/callback"));
        assertThat(resolver.resolve("neutral", client("carelog-mobile", ProductClientChannel.MOBILE)))
                .isEqualTo(URI.create("carelog://oauth/callback"));
        assertUnsupported(() -> resolver.resolve("neutral", client("finance-web", ProductClientChannel.WEB)));
    }

    @Test
    void 새_provider_client_설정은_legacy_설정보다_우선한다() {
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(
                properties(Map.of("neutral", Map.of("carelog-web", "https://new.example/callback"))),
                new MockEnvironment().withProperty(
                        "oauth.redirect-uris.neutral.WEB", "https://legacy.example/callback")
        );

        assertThat(resolver.resolve("neutral", client("carelog-web", ProductClientChannel.WEB)))
                .isEqualTo(URI.create("https://new.example/callback"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "https://[invalid"})
    void 새_설정이_blank_또는_invalid이면_legacy로_fallback하지_않는다(String callbackUri) {
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(
                properties(Map.of("neutral", Map.of("carelog-web", callbackUri))),
                new MockEnvironment().withProperty(
                        "oauth.redirect-uris.neutral.WEB", "https://legacy.example/callback")
        );

        assertUnsupported(() -> resolver.resolve("neutral", client("carelog-web", ProductClientChannel.WEB)));
    }

    @Test
    void client_mapping이_없으면_fail_closed로_거부한다() {
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(new OAuthProviderCallbackProperties(), new MockEnvironment());

        assertUnsupported(() -> resolver.resolve("neutral", client("carelog-web", ProductClientChannel.WEB)));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "callback", "https://[invalid"})
    void 빈값과_유효하지_않은_callback_URI는_fail_closed로_거부한다(String callbackUri) {
        OAuthRedirectUriResolver resolver = new OAuthRedirectUriResolver(
                properties(Map.of("neutral", Map.of("carelog-web", callbackUri))),
                new MockEnvironment()
        );

        assertUnsupported(() -> resolver.resolve("neutral", client("carelog-web", ProductClientChannel.WEB)));
    }

    private OAuthProviderCallbackProperties properties(Map<String, Map<String, String>> values) {
        OAuthProviderCallbackProperties properties = new OAuthProviderCallbackProperties();
        properties.setProviderCallbacks(values);
        return properties;
    }

    private RegisteredProductClient client(String clientId, ProductClientChannel channel) {
        return new RegisteredProductClient(clientId, Product.CARELOG, channel);
    }

    private void assertUnsupported(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation) {
        assertThatThrownBy(invocation)
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.UNSUPPORTED_CLIENT_CHANNEL);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuthProviderCallbackProperties.class)
    static class CallbackPropertiesConfiguration {
    }
}
