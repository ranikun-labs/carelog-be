package carelog.carelog.user.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityClaimsInternalApiPropertiesTest {

    @Test
    @DisplayName("enabled 상태에서 service token이 비어 있으면 startup validation이 실패한다")
    void enabledWithoutServiceToken_failsFast() {
        IdentityClaimsInternalApiProperties properties =
                new IdentityClaimsInternalApiProperties(true, " ");

        assertThatThrownBy(properties::requiredServiceToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("carelog.internal.identity-claims.service-token must be configured when "
                        + "carelog.internal.identity-claims.enabled=true");
    }
}
