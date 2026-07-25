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
 * PlatformAccount에 1:1로 종속되는 로그인 자격증명. accountId를 PK로 둬서 1:1과
 * accountId unique 요건을 동시에 만족시킨다(V2/V4 마이그레이션 참고).
 */
@Entity
@Table(name = "password_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PasswordCredential {

    @Id
    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID accountId;

    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private PasswordCredential(UUID accountId, String loginId, String passwordHash) {
        this.accountId = accountId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
    }

    public static PasswordCredential create(UUID accountId, String loginId, String passwordHash) {
        return new PasswordCredential(accountId, loginId, passwordHash);
    }
}
