package carelog.carelog.auth.domain;

import carelog.carelog.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

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

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "token_expires_at", nullable = false)
    private OffsetDateTime tokenExpiresAt;

    @Builder
    public RefreshToken(String refreshToken, String userId, OffsetDateTime tokenExpiresAt) {
        this.refreshToken = refreshToken;
        this.userId = userId;
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