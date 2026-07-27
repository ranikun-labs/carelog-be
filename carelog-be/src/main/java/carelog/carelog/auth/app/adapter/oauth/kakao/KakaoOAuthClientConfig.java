package carelog.carelog.auth.app.adapter.oauth.kakao;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/** Kakao API에만 사용하는 timeout 고정 RestClient다. */
@Configuration
@Conditional(KakaoOAuthConfiguredCondition.class)
@EnableConfigurationProperties(KakaoOAuthProperties.class)
public class KakaoOAuthClientConfig {

    @Bean
    RestClient kakaoOAuthRestClient(KakaoOAuthProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }
}
