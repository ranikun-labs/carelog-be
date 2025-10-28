package carelog.carelog.relation.domain;

import carelog.carelog.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Long> {

    Optional<Relation> findByManagerAndCustomer(User manager, User customer);
}
