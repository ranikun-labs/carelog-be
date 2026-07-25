package carelog.carelog.relation.app;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.relation.domain.*;
import carelog.carelog.relation.web.dto.*;

import java.util.*;

public interface RelationService {

    RelationResponse createRelation(UUID customerPublicId, UserPrincipal userDetails);

    RelationResponse findRelationByPublicId(UUID relationPublicId, UserPrincipal userDetails);

    RelationResponse findRelationByManagerAndCustomer(UUID managerPublicId, UUID customerPublicId, UserPrincipal userDetails);

    List<RelationResponse> findAllRelationsByManager(UserPrincipal userDetails);

    List<RelationResponse> findAllRelationsByCustomer(UUID customerPublicId, UserPrincipal userDetails);

    RelationResponse updateRelationsStatus(UUID relationPublicId, RelationStatus newStatus, UserPrincipal userDetails);

    void deleteRelation(UUID relationPublicId, UserPrincipal userDetails);
}
