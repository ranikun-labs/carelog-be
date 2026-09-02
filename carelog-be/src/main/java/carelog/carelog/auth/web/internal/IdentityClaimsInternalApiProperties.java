package carelog.carelog.auth.web.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADR-0019 Slice A′ internal claims projection의 opt-in runtime 설정.
 *
 * <p>{@code enabled}이 false이면 {@link InternalIdentitySecurityConfiguration}이 통째로
 * 등록되지 않으므로 이 record의 {@code serviceSecret}은 읽히지 않는다. {@code service-token}
 * (구 PR #44 계약)은 폐기되었고, 이 record는 {@code service-secret}만 갖는다.
 */
@ConfigurationProperties(prefix = "carelog.internal.identity-claims")
public record IdentityClaimsInternalApiProperties(
        boolean enabled,
        String serviceSecret
) {
}
