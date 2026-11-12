package carelog.carelog.auth.app.oauth;

import carelog.carelog.auth.app.port.oauth.OAuthBoundProductClient;
import carelog.carelog.auth.app.port.oauth.OAuthStateRecord;
import carelog.carelog.auth.app.port.productclient.ProductClientReader;
import carelog.carelog.auth.app.port.productclient.RegisteredProductClient;
import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthStateBindingVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final URI REDIRECT_URI = URI.create("https://app.example.com/oauth/callback");
    private static final RegisteredProductClient REGISTERED_CLIENT = new RegisteredProductClient(
            "carelog-web", Product.CARELOG, ProductClientChannel.WEB);

    @Mock private ProductClientReader productClientReader;

    private OAuthStateBindingVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new OAuthStateBindingVerifier(
                productClientReader, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void current_version의_유효한_provider_시간_Client_binding은_통과한다() {
        when(productClientReader.requireEnabled("carelog-web")).thenReturn(REGISTERED_CLIENT);

        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REGISTERED_CLIENT,
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isTrue();
    }

    @Test
    void unknown_version은_fail_closed한다() {
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION + 1,
                "neutral",
                REGISTERED_CLIENT,
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
    }

    @Test
    void issuedAt이_미래이면_fail_closed한다() {
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REGISTERED_CLIENT,
                NOW.plusSeconds(1),
                NOW.plusSeconds(2)))).isFalse();
    }

    @Test
    void expiresAt이_현재와_같거나_과거이면_fail_closed한다() {
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REGISTERED_CLIENT,
                NOW.minusSeconds(2),
                NOW))).isFalse();
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REGISTERED_CLIENT,
                NOW.minusSeconds(2),
                NOW.minusSeconds(1)))).isFalse();
    }

    @Test
    void provider가_다르면_fail_closed한다() {
        assertThat(verifier.verify("other-provider", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REGISTERED_CLIENT,
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
    }

    @Test
    void product_client를_찾을_수_없거나_비활성화면_fail_closed한다() {
        when(productClientReader.requireEnabled("unknown-client"))
                .thenThrow(new CustomException(ExceptionStatus.UNKNOWN_PRODUCT_CLIENT));
        when(productClientReader.requireEnabled("disabled-client"))
                .thenThrow(new CustomException(ExceptionStatus.DISABLED_PRODUCT_CLIENT));

        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                client("unknown-client", Product.CARELOG, ProductClientChannel.WEB),
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                client("disabled-client", Product.CARELOG, ProductClientChannel.WEB),
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
    }

    @Test
    void clientId_product_channel이_등록_snapshot과_다르면_fail_closed한다() {
        when(productClientReader.requireEnabled("other-client")).thenReturn(REGISTERED_CLIENT);
        when(productClientReader.requireEnabled("carelog-web")).thenReturn(REGISTERED_CLIENT);

        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                client("other-client", Product.CARELOG, ProductClientChannel.WEB),
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                client("carelog-web", Product.FINANCE_HARNESS, ProductClientChannel.WEB),
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
        assertThat(verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                client("carelog-web", Product.CARELOG, ProductClientChannel.MOBILE),
                NOW.minusSeconds(1),
                NOW.plusSeconds(1)))).isFalse();
    }

    @Test
    void legacy_state는_버전_snapshot_만료시각이_없으면_fail_closed한다() {
        OAuthStateRecord legacy = new OAuthStateRecord(
                0,
                "neutral",
                REDIRECT_URI,
                null,
                "/journals/42",
                "server-only-verifier",
                null,
                NOW.minusSeconds(1),
                null);

        assertThat(verifier.verify("neutral", legacy)).isFalse();
    }

    @Test
    void 인프라_성격의_registry_exception은_기존_전파_semantics를_보존한다() {
        when(productClientReader.requireEnabled("carelog-web"))
                .thenThrow(new CustomException(ExceptionStatus.OAUTH_STATE_STORE_UNAVAILABLE));

        assertThatThrownBy(() -> verifier.verify("neutral", state(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                REGISTERED_CLIENT,
                NOW.minusSeconds(1),
                NOW.plusSeconds(1))))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("exceptionStatus", ExceptionStatus.OAUTH_STATE_STORE_UNAVAILABLE);
    }

    private OAuthStateRecord state(
            int version,
            String provider,
            RegisteredProductClient client,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return state(version, provider,
                new OAuthBoundProductClient(client.clientId(), client.product(), client.channel()),
                issuedAt, expiresAt);
    }

    private OAuthStateRecord state(
            int version,
            String provider,
            OAuthBoundProductClient client,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new OAuthStateRecord(
                version,
                provider,
                REDIRECT_URI,
                client,
                "/journals/42",
                "server-only-verifier",
                "nonce",
                issuedAt,
                expiresAt);
    }

    private OAuthBoundProductClient client(
            String clientId,
            Product product,
            ProductClientChannel channel
    ) {
        return new OAuthBoundProductClient(clientId, product, channel);
    }
}
