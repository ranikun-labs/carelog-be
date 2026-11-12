package carelog.carelog.auth.app.port.oauth;

import carelog.carelog.auth.domain.Product;
import carelog.carelog.auth.domain.ProductClientChannel;

import java.util.Objects;

/** Authorization 시점에 검증한 Product Client의 State snapshot이다. */
public record OAuthBoundProductClient(
        String clientId,
        Product product,
        ProductClientChannel channel
) {

    public OAuthBoundProductClient {
        Objects.requireNonNull(clientId, "clientId must not be null");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
    }
}
