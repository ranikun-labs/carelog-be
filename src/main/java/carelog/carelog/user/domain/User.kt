package carelog.carelog.user.domain

import carelog.carelog.common.domain.TenantBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.OffsetDateTime

@Entity
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Table(name = "users")
class User (
    @Column(name = "user_id", unique = true, nullable = false)
    val userId: String,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    var password: String,

    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: UserRole,

    @Column(name = "phone_encrypted")
    var phoneEncrypted: String,

    @Column(name = "address_encrypted")
    var addressEncrypted: String,
): TenantBaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    val id: Long = 0

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null
        protected set


    fun updatePhoneEncrypted(phone: String) { this.phoneEncrypted = phone }
    fun updatePassword(password: String) { this.password = password }
    fun updateAddressEncrypted(address: String) { this.addressEncrypted = address }
}
