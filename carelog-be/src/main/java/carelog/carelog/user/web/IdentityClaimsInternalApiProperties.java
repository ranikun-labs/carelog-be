package carelog.carelog.user.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Private Carelog Product claims API runtime settings. */
@ConfigurationProperties(prefix = "carelog.internal.identity-claims")
public record IdentityClaimsInternalApiProperties(
        boolean enabled,
        String serviceToken
) {

    public String requiredServiceToken() {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException(
                    "carelog.internal.identity-claims.service-token must be configured when "
                            + "carelog.internal.identity-claims.enabled=true"
            );
        }
        return serviceToken;
    }
}
