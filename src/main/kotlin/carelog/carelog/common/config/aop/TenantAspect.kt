package carelog.carelog.common.config.aop

import carelog.carelog.common.config.TenantContext
import jakarta.persistence.EntityManager
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.hibernate.Session
import org.springframework.stereotype.Component

@Aspect
@Component
class TenantAspect(
    private val entityManager: EntityManager,
) {
    @Before("execution(* carelog.carelog..app.*ServiceImpl.*(..))")
    fun enableTenantFilter() {
        val organizationId = TenantContext.get() ?: return
        entityManager.unwrap(Session::class.java)
            .enableFilter("organizationFilter")
            .setParameter("organizationId", organizationId)
    }
}