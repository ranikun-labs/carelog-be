package carelog.carelog.relation.domain

import carelog.carelog.common.domain.TenantBaseEntity
import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import carelog.carelog.user.domain.User
import carelog.carelog.user.domain.UserRole
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@SQLDelete(sql = "UPDATE relations SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "relations")
class Relation private constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", referencedColumnName = "id", nullable = false)
    val manager: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "id", nullable = false)
    val customer: User,

    @Enumerated(EnumType.STRING)
    var status: RelationStatus,
) : TenantBaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    val id: Long = 0

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    val publicId: UUID = UUID.randomUUID()

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null

    fun updateStatus(status: RelationStatus) {
        this.status = status
    }

    companion object {
        fun create(manager: User, customer: User): Relation {
            if (manager.role != UserRole.MANAGER) throw CustomException(ExceptionStatus.INVALID_USER_ROLE)
            if (customer.role != UserRole.CUSTOMER) throw CustomException(ExceptionStatus.INVALID_USER_ROLE)
            return Relation(manager, customer, RelationStatus.ACTIVE)
        }
    }
}
