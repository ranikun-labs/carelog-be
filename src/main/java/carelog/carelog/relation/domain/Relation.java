package carelog.carelog.relation.domain;

import carelog.carelog.common.domain.TenantBaseEntity;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@SQLDelete(sql = "UPDATE relations SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "relations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Relation extends TenantBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", referencedColumnName = "id", nullable = false)
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    private RelationStatus status;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    private Relation(User manager, User customer, RelationStatus status) {
        this.publicId = UUID.randomUUID();
        this.manager = manager;
        this.customer = customer;
        this.status = status;
    }

    public static Relation create(User manager, User customer) {
        if (manager.getRole() != UserRole.MANAGER) {
            throw new CustomException(ExceptionStatus.INVALID_USER_ROLE);
        }
        if (customer.getRole() != UserRole.CUSTOMER) {
            throw new CustomException(ExceptionStatus.INVALID_USER_ROLE);
        }

        return new Relation(manager, customer, RelationStatus.ACTIVE);
    }

    public void updateStatus(RelationStatus status) {
        this.status = status;
    }
}
