package carelog.carelog.user.domain;

import carelog.carelog.common.domain.TenantBaseEntity;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TenantBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", unique = true)
    private String userId;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_type")
    private ManagerType managerType;

    @Column(name = "phone_encrypted")
    private String phoneEncrypted;

    @Column(name = "address_encrypted")
    private String addressEncrypted;

    @Column(name = "customer_memo", columnDefinition = "text")
    private String customerMemo;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // Identity Foundation: 로그인 가능한 MANAGER만 연결(nullable). CUSTOMER는 Platform Account가 아니다.
    @Column(name = "account_id")
    private UUID accountId;

    @Builder
    public User(
            String userId, String email, String password, String name,
            UserRole role, ManagerType managerType, String phoneEncrypted, String addressEncrypted,
            String customerMemo
    ) {
        /**
         * Manager 불변식 검증 - 객체 생성 시, 바로 실패
         * - DTO에서는 외부입력 방어, Enttiy에서 도메인 규칙 자체를 보장
         */
        if (role == UserRole.MANAGER) {
            if (managerType == null || userId == null
            || email == null || password == null|| name == null) {
                throw new CustomException(ExceptionStatus.INVALID_MANAGER_FIELDS);
            }
        }
        this.publicId = UUID.randomUUID();
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.managerType = managerType;
        this.phoneEncrypted = phoneEncrypted;
        this.addressEncrypted = addressEncrypted;
        this.customerMemo = customerMemo;
    }

    public void updatePhoneEncrypted(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateAddressEncrypted(String addressEncrypted) {
        this.addressEncrypted = addressEncrypted;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateCustomerMemo(String customerMemo) {
        this.customerMemo = customerMemo;
    }

    public void assignAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    /**
     * Identity Account 등록 결과와 publicId를 일치시킨다(MANAGER 전용, 신규 가입 경로에서 사용).
     * Backfill(V3)이 기존 MANAGER의 publicId를 platform_accounts.id로 그대로 승계한 것과 동일한
     * 불변식 — accountId == publicId — 을 신규 가입에도 유지하기 위함이다. updatable=false는
     * 이후 UPDATE만 막을 뿐, 이 메서드가 save() 이전(INSERT 전)에 호출되는 한 안전하다.
     */
    public void assignPublicId(UUID publicId) {
        this.publicId = publicId;
    }
}
