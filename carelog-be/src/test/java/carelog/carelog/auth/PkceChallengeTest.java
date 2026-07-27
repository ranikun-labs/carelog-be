package carelog.carelog.auth;

import carelog.carelog.auth.app.port.oauth.PkceChallenge;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PkceChallengeTest {

    @Test
    void S256_verifier와_challenge를_생성한다() throws Exception {
        PkceChallenge challenge = PkceChallenge.generate();

        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(challenge.codeVerifier().getBytes(StandardCharsets.US_ASCII))
        );

        assertThat(challenge.codeVerifier()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(challenge.codeChallenge()).isEqualTo(expected);
        assertThat(challenge.method()).isEqualTo("S256");
    }

    @Test
    void RFC7636_고정_벡터로_S256_challenge를_검증한다() {
        PkceChallenge challenge = PkceChallenge.fromCodeVerifier(
                "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        );

        assertThat(challenge.codeChallenge())
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        assertThat(challenge.method()).isEqualTo("S256");
    }

    @Test
    void 매_호출마다_새로운_PKCE_값을_생성한다() {
        assertThat(PkceChallenge.generate().codeVerifier())
                .isNotEqualTo(PkceChallenge.generate().codeVerifier());
    }
}
