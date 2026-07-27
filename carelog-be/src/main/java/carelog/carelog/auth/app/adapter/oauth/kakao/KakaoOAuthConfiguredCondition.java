package carelog.carelog.auth.app.adapter.oauth.kakao;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;

/** Kakao 설정이 전부 있을 때만 adapter bean을 등록하고, 일부 설정은 즉시 실패시킨다. */
public class KakaoOAuthConfiguredCondition implements Condition {

    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "carelog.auth.oauth.kakao.client-id",
            "carelog.auth.oauth.kakao.authorization-uri",
            "carelog.auth.oauth.kakao.token-uri",
            "carelog.auth.oauth.kakao.user-info-uri",
            "oauth.redirect-uris.kakao.WEB",
            "oauth.redirect-uris.kakao.MOBILE",
            "carelog.auth.oauth.kakao.client-secret",
            "carelog.auth.oauth.kakao.connect-timeout",
            "carelog.auth.oauth.kakao.read-timeout"
    );

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        long configured = REQUIRED_PROPERTIES.stream()
                .filter(key -> hasText(context.getEnvironment().getProperty(key)))
                .count();
        if (configured == 0) {
            return false;
        }
        long requiredConfigured = REQUIRED_PROPERTIES.subList(0, 6).stream()
                .filter(key -> hasText(context.getEnvironment().getProperty(key))).count();
        if (requiredConfigured != 6) {
            throw new IllegalStateException("Kakao OAuth 설정은 모두 제공하거나 모두 생략해야 합니다.");
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
