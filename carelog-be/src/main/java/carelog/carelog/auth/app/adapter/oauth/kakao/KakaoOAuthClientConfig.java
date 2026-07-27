package carelog.carelog.auth.app.adapter.oauth.kakao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.net.http.HttpClient;

/** Kakao API에만 사용하는 timeout 고정 RestClient다. */
@Configuration
@Conditional(KakaoOAuthConfiguredCondition.class)
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthClientConfig {

    @Bean
    RestClient kakaoOAuthRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder().requestFactory(factory).build();
    }
}
