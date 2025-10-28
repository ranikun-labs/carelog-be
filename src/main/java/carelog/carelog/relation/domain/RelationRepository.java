package carelog.carelog.relation.domain;

import carelog.carelog.user.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.*;

import java.util.*;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Long> {

    Optional<Relation> findByManagerAndCustomer(User manager, User customer);

    List<Relation> findAllManger(User user);

    List<Relation> findAllByCustomer(User customer);

    boolean existsByManagerAndCustomer(User manager, User customer);
}
