package carelog.carelog.auth.app.port.oauth;

import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthStateRecordTest {

    @Test
    void State_schema와_Product_Client_snapshot과_명시적_만료시각을_함께_보존한다() {
        Instant issuedAt = Instant.parse("2026-08-11T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-08-11T00:05:00Z");
        OAuthBoundProductClient productClient = new OAuthBoundProductClient(
                "carelog-web", Product.CARELOG, ProductClientChannel.WEB
        );

        OAuthStateRecord record = new OAuthStateRecord(
                OAuthStateRecord.CURRENT_VERSION,
                "neutral",
                URI.create("https://app.example.com/oauth/callback"),
                productClient,
                "/journals/42",
                "server-only-verifier",
                "nonce",
                issuedAt,
                expiresAt
        );

        assertThat(record.version()).isEqualTo(1);
        assertThat(record.productClient()).isEqualTo(productClient);
        assertThat(record.issuedAt()).isEqualTo(issuedAt);
        assertThat(record.expiresAt()).isEqualTo(expiresAt);
    }
}
