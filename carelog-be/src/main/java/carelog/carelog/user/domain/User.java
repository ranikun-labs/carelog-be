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

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Builder
    public User(
            String userId, String email, String password, String name,
            UserRole role,  ManagerType managerType, String phoneEncrypted, String addressEncrypted
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
}
