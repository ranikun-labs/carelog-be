package carelog.carelog.auth.web.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalServiceCredentialVerifierTest {

    private static final String SERVICE_SECRET =
            "platform-identity-service-secret-0123456789";
    private static final String GATEWAY_SECRET = "gateway-internal-secret";

    @Test
    void validCredentialCreatesOnlyTheFixedProjectionPrincipal() {
        InternalServiceCredentialVerifier verifier = verifier();

        assertThat(verifier.matches("platform-identity", SERVICE_SECRET)).isTrue();

        Authentication authentication = verifier.authenticatedPrincipal();
        assertThat(authentication.getName()).isEqualTo("platform-identity");
        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("PROJECTION_READ");
    }

    @Test
    void wrongServiceIdOrSecretDoesNotAuthenticate() {
        InternalServiceCredentialVerifier verifier = verifier();

        assertThat(verifier.matches("other-service", SERVICE_SECRET)).isFalse();
        assertThat(verifier.matches("platform-identity", "wrong-service-secret"))
                .isFalse();
        assertThat(verifier.matches("platform-identity", null)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short-service-secret"})
    void shortOrMissingServiceSecretFailsFast(String serviceSecret) {
        assertThatThrownBy(() -> new InternalServiceCredentialVerifier(
                serviceSecret, GATEWAY_SECRET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingGatewaySecretFailsFast() {
        assertThatThrownBy(() -> new InternalServiceCredentialVerifier(SERVICE_SECRET, " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void serviceSecretEqualToGatewaySecretFailsFast() {
        String equalSecret = "same-secret-that-is-at-least-thirty-two-characters";

        assertThatThrownBy(() -> new InternalServiceCredentialVerifier(equalSecret, equalSecret))
                .isInstanceOf(IllegalStateException.class);
    }

    private InternalServiceCredentialVerifier verifier() {
        return new InternalServiceCredentialVerifier(SERVICE_SECRET, GATEWAY_SECRET);
    }
}
