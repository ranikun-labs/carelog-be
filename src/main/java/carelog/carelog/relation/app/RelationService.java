package carelog.carelog.relation.app;

import carelog.carelog.relation.domain.Relation;
import carelog.carelog.relation.domain.RelationStatus;
import carelog.carelog.user.domain.User;

import java.util.List;
import java.util.Optional;

public interface RelationService {

    Relation createRelation(User manager, User customer);

    Optional<Relation> findRelationById(Long relationId);

    Optional<Relation> findRelationByManagerAndCustomer(User manager, User customer);

    List<Relation> findAllRelationsByManager(User manager);

    List<Relation> findAllRelationsByCustomer(User customer);

    Relation updateRelationsStatus(Long relationId, RelationStatus newStatus);

    void deleteRelation(Long relationId);
}
