package carelog.carelog.relation.domain

import carelog.carelog.user.domain.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface RelationRepository : JpaRepository<Relation, Long> {
    fun findByManagerAndCustomer(manager: User, customer: User): Optional<Relation>
    fun findAllByManager(manager: User): List<Relation>
    fun findAllByCustomer(customer: User): List<Relation>
    fun findByPublicId(publicId: UUID): Optional<Relation>
    fun existsByManagerAndCustomer(manager: User, customer: User): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r FROM Relation r where r.manager = :manager and r.customer = :customer")
    fun findByManagerAndCustomerForUpdate(
        @Param("manager") manager: User,
        @Param("customer") customer: User,
    ): Optional<Relation>
}
