package carelog.carelog.relation.domain;

import carelog.carelog.user.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.*;

import java.util.*;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Long> {

    Optional<Relation> findByManagerAndCustomer(User manager, User customer);

    List<Relation> findAllByManager(User manager);

    List<Relation> findAllByCustomer(User customer);

    boolean existsByManagerAndCustomer(User manager, User customer);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r FROM Relation r where r.manager = :manager and r.customer = :customer")
    Optional<Relation> findByManagerAndCustomerForUpdate(
            @Param("manager") User manager, @Param("customer") User customer
    );
}