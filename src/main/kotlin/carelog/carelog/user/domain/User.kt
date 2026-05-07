package carelog.carelog.user.domain

import carelog.carelog.common.domain.TenantBaseEntity
import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "users")
class User(
    @Column(name = "user_id", unique = true)
    val userId: String? = null,

    @Column(name = "email", unique = true)
    val email: String? = null,

    @Column(name = "password")
    var password: String? = null,

    @Column(name = "name", nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    val role: UserRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "manager_type")
    val managerType: ManagerType? = null,

    @Column(name = "phone_encrypted")
    var phoneEncrypted: String? = null,

    @Column(name = "address_encrypted")
    var addressEncrypted: String? = null,
) : TenantBaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    val id: Long = 0

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    val publicId: UUID = UUID.randomUUID()

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null

    init {
        if (role == UserRole.MANAGER &&
            (managerType == null || userId == null || email == null || password == null || name.isBlank())
        ) {
            throw CustomException(ExceptionStatus.INVALID_MANAGER_FIELDS)
        }
    }

    fun updatePassword(password: String) { this.password = password }
    fun updatePhoneEncrypted(phoneEncrypted: String) { this.phoneEncrypted = phoneEncrypted }
    fun updateAddressEncrypted(addressEncrypted: String) { this.addressEncrypted = addressEncrypted }
}
