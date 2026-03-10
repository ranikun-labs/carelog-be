package carelog.carelog.user.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): Optional<User>

    fun existsByEmail(email: String): Boolean

    fun findByUserId(userId: String): Optional<User>

    fun existsByUserId(userId: String): Boolean
}