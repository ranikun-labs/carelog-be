package carelog.carelog.user.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, Long> {
    fun findByPublicId(publicId: UUID): Optional<User>
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun findByUserId(userId: String): Optional<User>
    fun existsByUserId(userId: String): Boolean
    fun findAllByRole(role: UserRole): List<User>
    fun findAllByRoleAndNameContaining(role: UserRole, name: String): List<User>
}
