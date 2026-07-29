package carelog.carelog.auth.app.port.productclient;

/** OAuth Core가 Entity 없이 사용하는 활성 제품 Client 계약이다. */
public record RegisteredProductClient(
        String clientId,
        Product product,
        ProductClientChannel channel
) {
}
