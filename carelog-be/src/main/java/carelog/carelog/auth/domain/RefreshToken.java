package carelog.carelog.auth.domain;

import carelog.carelog.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long id;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 500)
    private String refreshToken;

    // 세션의 공식 조회 키(Identity Foundation B0). loginId가 없는 Principal(향후 OAuth)도 세션을 가질 수 있어야 하므로 nullable.
    @Column(name = "account_id")
    private UUID accountId;

    // Legacy Mirror: 과거 loginId 기반 조회의 흔적. 더 이상 조회 키로 쓰이지 않으며 신규 행에는 채우지 않는다(V5에서 NOT NULL 해제).
    @Column(name = "user_id")
    private String userId;

    @Column(name = "token_expires_at", nullable = false)
    private OffsetDateTime tokenExpiresAt;

    @Builder
    public RefreshToken(String refreshToken, UUID accountId, OffsetDateTime tokenExpiresAt) {
        this.refreshToken = refreshToken;
        this.accountId = accountId;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public boolean isExpired(OffsetDateTime now) {
        return now.isAfter(this.tokenExpiresAt);
    }

    public void updateToken(String refreshToken, OffsetDateTime tokenExpiresAt) {
        this.refreshToken = refreshToken;
        this.tokenExpiresAt = tokenExpiresAt;
    }
}