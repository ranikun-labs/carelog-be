package carelog.carelog.relation.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.relation.domain.RelationStatus
import carelog.carelog.relation.web.dto.RelationResponse
import java.util.UUID

interface RelationService {
    fun createRelation(customerPublicId: UUID, userDetails: CustomUserDetails): RelationResponse
    fun findRelationByPublicId(relationPublicId: UUID, userDetails: CustomUserDetails): RelationResponse
    fun findRelationByManagerAndCustomer(managerPublicId: UUID, customerPublicId: UUID, userDetails: CustomUserDetails): RelationResponse
    fun findAllRelationsByManager(userDetails: CustomUserDetails): List<RelationResponse>
    fun findAllRelationsByCustomer(customerPublicId: UUID, userDetails: CustomUserDetails): List<RelationResponse>
    fun updateRelationsStatus(relationPublicId: UUID, newStatus: RelationStatus, userDetails: CustomUserDetails): RelationResponse
    fun deleteRelation(relationPublicId: UUID, userDetails: CustomUserDetails)
}
