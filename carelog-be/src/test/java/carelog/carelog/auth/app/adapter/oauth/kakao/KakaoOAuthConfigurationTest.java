package carelog.carelog.auth.app.adapter.oauth.kakao;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test void 설정이_없으면_Kakao_bean이_없다() {
        runner.run(context -> { assertThat(context).hasNotFailed(); assertThat(context).doesNotHaveBean(KakaoOAuthProviderAdapter.class).doesNotHaveBean(KakaoOAuthApiClient.class); });
    }
    @Test void 필수_설정만으로_Kakao_bean을_등록한다() {
        configured(runner).run(context -> { assertThat(context).hasNotFailed(); assertThat(context).hasSingleBean(KakaoOAuthProviderAdapter.class).hasSingleBean(KakaoOAuthApiClient.class); });
    }
    @Test void 선택_설정만_있으면_안전하게_실패한다() {
        runner.withPropertyValues("carelog.auth.oauth.kakao.client-secret=secret-value").run(context -> {
            assertThat(context).hasFailed(); assertThat(context.getStartupFailure().toString()).doesNotContain("secret-value");
        });
    }
    private ApplicationContextRunner configured(ApplicationContextRunner value) {
        return value.withPropertyValues("carelog.auth.oauth.kakao.client-id=id", "carelog.auth.oauth.kakao.authorization-uri=https://a.example", "carelog.auth.oauth.kakao.token-uri=https://t.example", "carelog.auth.oauth.kakao.user-info-uri=https://u.example", "oauth.redirect-uris.kakao.WEB=https://web.example", "oauth.redirect-uris.kakao.MOBILE=carelog://callback");
    }
    @Configuration @Import({KakaoOAuthClientConfig.class, KakaoOAuthApiClient.class, KakaoOAuthErrorMapper.class, KakaoOAuthProviderAdapter.class})
    static class TestConfig { @Bean Clock clock() { return Clock.systemUTC(); } }
}
