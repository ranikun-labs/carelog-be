package carelog.carelog.identity.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Provider-neutral 외부 신원 연결. 한 PlatformAccount에 여러 External Identity가 연결될 수 있다
 * (unique(provider, provider_subject)). 이번 Phase에서는 실제 Provider Adapter/OAuth 흐름을
 * 만들지 않으며, 향후 연결을 위한 저장 구조만 준비한다.
 */
@Entity
@Table(name = "external_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "email_snapshot")
    private String emailSnapshot;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private ExternalIdentity(UUID accountId, String provider, String providerSubject, String emailSnapshot) {
        this.accountId = accountId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.emailSnapshot = emailSnapshot;
    }

    public static ExternalIdentity create(UUID accountId, String provider, String providerSubject, String emailSnapshot) {
        return new ExternalIdentity(accountId, provider, providerSubject, emailSnapshot);
    }
}
