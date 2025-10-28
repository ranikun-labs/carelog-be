package carelog.carelog.relation.domain;

import carelog.carelog.common.domain.BaseEntity;
import carelog.carelog.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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

    @Builder
    public Relation(User manager, User customer, RelationStatus status) {
        this.manager = manager;
        this.customer = customer;
        this.status = status;
    }

    public void updateStatus(RelationStatus status) {
        this.status = status;
    }
}
