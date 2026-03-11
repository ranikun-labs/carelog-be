package carelog.carelog.common.config.aop;

import carelog.carelog.common.config.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class TenantAspect {

    private final EntityManager entityManager;

    @Before("execution(* carelog.carelog..app.*ServiceImpl.*(..))")
    public void enableTenantFilter() {
        UUID organizationId = TenantContext.get();
        if (organizationId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("organizationFilter")
                    .setParameter("organizationId", organizationId);
        }
    }
}
