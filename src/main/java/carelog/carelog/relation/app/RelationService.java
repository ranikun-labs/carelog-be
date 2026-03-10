package carelog.carelog.relation.app;

import carelog.carelog.relation.domain.*;
import carelog.carelog.relation.web.dto.*;

import java.util.*;

public interface RelationService {

    RelationResponse createRelation(Long managerId, Long customerId);

    RelationResponse findRelationById(Long relationId);

    RelationResponse findRelationByManagerAndCustomer(Long managerId, Long customerId);

    List<RelationResponse> findAllRelationsByManager(Long managerId);

    List<RelationResponse> findAllRelationsByCustomer(Long customerId);

    RelationResponse updateRelationsStatus(Long relationId, RelationStatus newStatus);

    void deleteRelation(Long relationId);
}
