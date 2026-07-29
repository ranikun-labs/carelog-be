package carelog.carelog.auth.domain;

import carelog.carelog.auth.app.port.productclient.Product;
import carelog.carelog.auth.app.port.productclient.ProductClientChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.Objects;

/** Auth/Identity 경계가 소유하는 제품·채널별 인증 Client 등록 정보다. */
@Entity
@Table(name = "product_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ProductClientChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ProductClient(String clientId, Product product, ProductClientChannel channel, boolean enabled) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        this.clientId = clientId;
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.enabled = enabled;
    }

    public static ProductClient create(
            String clientId, Product product, ProductClientChannel channel, boolean enabled
    ) {
        return new ProductClient(clientId, product, channel, enabled);
    }
}
