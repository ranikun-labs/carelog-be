package carelog.carelog.relation.domain;

import carelog.carelog.common.domain.*;
import carelog.carelog.common.web.exception.*;
import carelog.carelog.user.domain.*;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.*;

@Entity
@SQLDelete(sql = "UPDATE relations SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "relations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Relation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", referencedColumnName = "id", nullable = false)
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    private RelationStatus status;

    private Relation(User manager, User customer, RelationStatus status) {
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
