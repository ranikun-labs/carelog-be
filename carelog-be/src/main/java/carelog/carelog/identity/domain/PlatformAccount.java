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
 * Identity Principal(로그인 가능한 계정)의 Stable Account. 인증 가능한 Carelog MANAGER가 대상이며,
 * CRM Customer(UserRole.CUSTOMER)는 Platform Account로 만들지 않는다.
 *
 * <p>email은 자동 계정 병합 기준이 아니므로 unique로 강제하지 않는다(primary_email = snapshot).
 */
@Entity
@Table(name = "platform_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PlatformAccount {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "primary_email")
    private String primaryEmail;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private PlatformAccount(String primaryEmail) {
        this.id = UUID.randomUUID();
        this.status = AccountStatus.ACTIVE;
        this.primaryEmail = primaryEmail;
    }

    public static PlatformAccount create(String primaryEmail) {
        return new PlatformAccount(primaryEmail);
    }
}
