package carelog.carelog.auth.web.internal;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * ADR-0019가 승인한 단일 interim 서비스 자격 증명({@code X-Service-Id}/{@code X-Service-Secret})을
 * 검증한다.
 *
 * <p>비교는 길이 정보를 노출하지 않도록 고정 길이 digest + {@link MessageDigest#isEqual}로 수행한다.
 * secret은 어떤 로그·응답·예외 메시지에도 원문으로 담기지 않는다.
 */
public final class InternalServiceCredentialVerifier {

    public static final String SERVICE_ID = "platform-identity";
    public static final String PROJECTION_READ_AUTHORITY = "PROJECTION_READ";
    public static final String SERVICE_PRINCIPAL = SERVICE_ID;

    private static final int MIN_SERVICE_SECRET_LENGTH = 32;

    private final byte[] serviceSecretDigest;

    public InternalServiceCredentialVerifier(String serviceSecret, String gatewayInternalSecret) {
        validate(serviceSecret, gatewayInternalSecret);
        this.serviceSecretDigest = digest(serviceSecret);
    }

    public boolean matches(String serviceId, String serviceSecret) {
        return constantTimeEquals(SERVICE_ID, serviceId)
                && serviceSecret != null
                && MessageDigest.isEqual(serviceSecretDigest, digest(serviceSecret));
    }

    public Authentication authenticatedPrincipal() {
        return new UsernamePasswordAuthenticationToken(
                SERVICE_PRINCIPAL,
                null,
                AuthorityUtils.createAuthorityList(PROJECTION_READ_AUTHORITY));
    }

    private static void validate(String serviceSecret, String gatewayInternalSecret) {
        if (serviceSecret == null
                || serviceSecret.isBlank()
                || serviceSecret.length() < MIN_SERVICE_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "carelog.internal.identity-claims.service-secret must be at least 32 characters");
        }
        if (gatewayInternalSecret == null || gatewayInternalSecret.isBlank()) {
            throw new IllegalStateException("gateway.internal-secret must be configured");
        }
        if (constantTimeEquals(serviceSecret, gatewayInternalSecret)) {
            throw new IllegalStateException(
                    "carelog.internal.identity-claims.service-secret must differ from gateway.internal-secret");
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return actual != null
                && MessageDigest.isEqual(digest(expected), digest(actual));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
