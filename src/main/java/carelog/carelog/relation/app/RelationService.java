package carelog.carelog.relation.app;

import carelog.carelog.relation.domain.Relation;
import carelog.carelog.relation.domain.RelationStatus;
import carelog.carelog.user.domain.User;

import java.util.List;
import java.util.Optional;

public interface RelationService {

    Relation createRelation(Long managerId, Long customerId);

    Optional<Relation> findRelationById(Long relationId);

    Optional<Relation> findRelationByManagerAndCustomer(Long managerId, Long customerId);

    List<Relation> findAllRelationsByManager(Long managerId);

    List<Relation> findAllRelationsByCustomer(Long customerId);

    Relation updateRelationsStatus(Long relationId, RelationStatus newStatus);

    void deleteRelation(Long relationId);
}
