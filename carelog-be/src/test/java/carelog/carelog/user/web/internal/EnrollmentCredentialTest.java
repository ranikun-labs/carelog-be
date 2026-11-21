package carelog.carelog.user.web.internal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentCredentialTest {
    private static final String WRITE = "dedicated-write-secret-01234567890123456789";
    private static final String READ = "separate-read-secret-012345678901234567890";
    private static final String GATEWAY = "gateway-secret";

    @Test
    void requiresDedicatedLongCredentialAndGatewaySecret() {
        assertThatCode(() -> EnrollmentSecurityConfiguration.validateSecrets(WRITE, READ, GATEWAY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EnrollmentSecurityConfiguration.validateSecrets("short", READ, GATEWAY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentSecurityConfiguration.validateSecrets(WRITE, WRITE, GATEWAY)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentSecurityConfiguration.validateSecrets(WRITE, READ, WRITE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentSecurityConfiguration.validateSecrets(WRITE, READ, "")).isInstanceOf(IllegalStateException.class);
    }
}
