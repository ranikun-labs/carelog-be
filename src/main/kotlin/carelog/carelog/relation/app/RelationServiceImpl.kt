package carelog.carelog.relation.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import carelog.carelog.relation.domain.Relation
import carelog.carelog.relation.domain.RelationRepository
import carelog.carelog.relation.domain.RelationStatus
import carelog.carelog.relation.web.dto.RelationResponse
import carelog.carelog.user.domain.UserRepository
import carelog.carelog.user.domain.UserRole
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class RelationServiceImpl(
    private val relationRepository: RelationRepository,
    private val userRepository: UserRepository,
) : RelationService {

    @Transactional
    override fun createRelation(customerPublicId: UUID, userDetails: CustomUserDetails): RelationResponse {
        val manager = userRepository.findByUserId(userDetails.username)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }
        val customer = userRepository.findByPublicId(customerPublicId)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }

        if (manager.role != UserRole.MANAGER || customer.role != UserRole.CUSTOMER) {
            throw CustomException(ExceptionStatus.INVALID_USER_ROLE)
        }

        relationRepository.findByManagerAndCustomerForUpdate(manager, customer)
            .ifPresent { throw CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS) }

        val relation = Relation.create(manager, customer)
        relation.assignOrganization(userDetails.organizationId)

        return RelationResponse.from(relationRepository.save(relation))
    }

    override fun findRelationByPublicId(relationPublicId: UUID, userDetails: CustomUserDetails): RelationResponse {
        val relation = relationRepository.findByPublicId(relationPublicId)
            .orElseThrow { CustomException(ExceptionStatus.RELATION_NOT_FOUND) }

        if (userDetails.publicId != relation.manager.publicId &&
            userDetails.publicId != relation.customer.publicId
        ) {
            throw CustomException(ExceptionStatus.ACCESS_DENIED)
        }

        return RelationResponse.from(relation)
    }

    override fun findRelationByManagerAndCustomer(
        managerPublicId: UUID,
        customerPublicId: UUID,
        userDetails: CustomUserDetails,
    ): RelationResponse {
        if (userDetails.publicId != managerPublicId && userDetails.publicId != customerPublicId) {
            throw CustomException(ExceptionStatus.ACCESS_DENIED)
        }

        val manager = userRepository.findByPublicId(managerPublicId)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }
        val customer = userRepository.findByPublicId(customerPublicId)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }

        return relationRepository.findByManagerAndCustomer(manager, customer)
            .map(RelationResponse::from)
            .orElseThrow { CustomException(ExceptionStatus.RELATION_NOT_FOUND) }
    }

    override fun findAllRelationsByManager(userDetails: CustomUserDetails): List<RelationResponse> {
        val manager = userRepository.findByUserId(userDetails.username)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }
        return relationRepository.findAllByManager(manager).map(RelationResponse::from)
    }

    override fun findAllRelationsByCustomer(customerPublicId: UUID, userDetails: CustomUserDetails): List<RelationResponse> {
        if (userDetails.publicId != customerPublicId) {
            throw CustomException(ExceptionStatus.ACCESS_DENIED)
        }

        val customer = userRepository.findByPublicId(customerPublicId)
            .orElseThrow { CustomException(ExceptionStatus.USER_NOT_FOUND) }
        return relationRepository.findAllByCustomer(customer).map(RelationResponse::from)
    }

    @Transactional
    override fun updateRelationsStatus(
        relationPublicId: UUID,
        newStatus: RelationStatus,
        userDetails: CustomUserDetails,
    ): RelationResponse {
        val relation = relationRepository.findByPublicId(relationPublicId)
            .orElseThrow { CustomException(ExceptionStatus.RELATION_NOT_FOUND) }

        if (userDetails.publicId != relation.manager.publicId) {
            throw CustomException(ExceptionStatus.ACCESS_DENIED)
        }

        relation.updateStatus(newStatus)
        return RelationResponse.from(relation)
    }

    @Transactional
    override fun deleteRelation(relationPublicId: UUID, userDetails: CustomUserDetails) {
        val relation = relationRepository.findByPublicId(relationPublicId)
            .orElseThrow { CustomException(ExceptionStatus.RELATION_NOT_FOUND) }

        if (userDetails.publicId != relation.manager.publicId) {
            throw CustomException(ExceptionStatus.ACCESS_DENIED)
        }

        relationRepository.delete(relation)
    }
}
