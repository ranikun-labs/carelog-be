package carelog.carelog.relation.app;

import carelog.carelog.relation.domain.*;
import carelog.carelog.relation.web.dto.*;

import java.util.*;

public interface RelationService {

    RelationResponse createRelation(UUID customerPublicId);

    RelationResponse findRelationByPublicId(UUID relationPublicId);

    RelationResponse findRelationByManagerAndCustomer(UUID managerPublicId, UUID customerPublicId);

    List<RelationResponse> findAllRelationsByManager();

    List<RelationResponse> findAllRelationsByCustomer(UUID customerPublicId);

    RelationResponse updateRelationsStatus(UUID relationPublicId, RelationStatus newStatus);

    void deleteRelation(UUID relationPublicId);
}
