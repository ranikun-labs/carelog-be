package carelog.carelog.auth.app.port.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** RFC 7636 S256 PKCE challenge다. plain 방식은 지원하지 않는다. */
public record PkceChallenge(
        String codeVerifier,
        String codeChallenge,
        String method
) {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    public static PkceChallenge generate() {
        byte[] verifierBytes = new byte[32];
        SECURE_RANDOM.nextBytes(verifierBytes);
        String verifier = BASE64_URL_ENCODER.encodeToString(verifierBytes);
        return fromCodeVerifier(verifier);
    }

    /** 주어진 verifier에서 S256 challenge를 유도한다. 호출자는 verifier의 난수성을 보장해야 한다. */
    public static PkceChallenge fromCodeVerifier(String codeVerifier) {
        return new PkceChallenge(codeVerifier, s256(codeVerifier), "S256");
    }

    private static String s256(String verifier) {
        try {
            return BASE64_URL_ENCODER.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
