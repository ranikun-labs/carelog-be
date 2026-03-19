package carelog.carelog.relation.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.relation.domain.*;
import carelog.carelog.relation.web.dto.*;

import java.util.*;

public interface RelationService {

    RelationResponse createRelation(UUID customerPublicId, CustomUserDetails userDetails);

    RelationResponse findRelationByPublicId(UUID relationPublicId, CustomUserDetails userDetails);

    RelationResponse findRelationByManagerAndCustomer(UUID managerPublicId, UUID customerPublicId, CustomUserDetails userDetails);

    List<RelationResponse> findAllRelationsByManager(CustomUserDetails userDetails);

    List<RelationResponse> findAllRelationsByCustomer(UUID customerPublicId, CustomUserDetails userDetails);

    RelationResponse updateRelationsStatus(UUID relationPublicId, RelationStatus newStatus, CustomUserDetails userDetails);

    void deleteRelation(UUID relationPublicId, CustomUserDetails userDetails);
}
