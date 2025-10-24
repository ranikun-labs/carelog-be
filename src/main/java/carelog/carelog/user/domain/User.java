package carelog.carelog.user.domain;

import carelog.carelog.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "phone_encrypted")
    private String phoneEncrypted;

    @Column(name = "address_encrypted")
    private String addressEncrypted;

    @Builder
    public User(
            String email, String password, String name,
            UserRole role, String phoneEncrypted, String addressEncrypted) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
        this.phoneEncrypted = phoneEncrypted;
        this.addressEncrypted = addressEncrypted;
    }
}
