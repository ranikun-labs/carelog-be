package carelog.carelog.user.domain;

import carelog.carelog.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private String userId;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    private ManagerType managerType;

    @Column(name = "phone_encrypted")
    private String phoneEncrypted;

    @Column(name = "address_encrypted")
    private String addressEncrypted;

    @Builder
    public User(
            String userId, String email, String password, String name,
            UserRole role, ManagerType managerType, String phoneEncrypted, String addressEncrypted) {
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
