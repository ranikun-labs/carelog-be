package carelog.carelog.relation.app;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.common.web.exception.*;
import carelog.carelog.relation.domain.*;
import carelog.carelog.relation.web.dto.*;
import carelog.carelog.user.domain.*;
import lombok.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RelationServiceImpl implements RelationService {

    private final RelationRepository relationRepository;
    private final UserRepository userRepository;

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public RelationResponse createRelation(UUID customerPublicId, UserPrincipal userDetails) {
        User manager = userRepository.findByAccountId(userDetails.getAccountId())
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        if (manager.getRole() != UserRole.MANAGER || customer.getRole() != UserRole.CUSTOMER) {
            throw new CustomException(ExceptionStatus.INVALID_USER_ROLE);
        }

        relationRepository.findByManagerAndCustomerForUpdate(manager, customer)
                .ifPresent(r -> { throw new CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS); });

        Relation relation = Relation.create(manager, customer);
        relation.assignOrganization(userDetails.getOrganizationId());

        RelationResponse response = RelationResponse.from(relationRepository.save(relation));
        return response;
    }

    @Override
    public RelationResponse findRelationByPublicId(UUID relationPublicId, UserPrincipal userDetails) {
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!userDetails.getPublicId().equals(relation.getManager().getPublicId()) &&
                !userDetails.getPublicId().equals(relation.getCustomer().getPublicId())) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        RelationResponse response = RelationResponse.from(relation);
        return response;
    }

    @Override
    public RelationResponse findRelationByManagerAndCustomer(UUID managerPublicId, UUID customerPublicId, UserPrincipal userDetails) {
        if (!userDetails.getPublicId().equals(managerPublicId) &&
                !userDetails.getPublicId().equals(customerPublicId)) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        User manager = userRepository.findByPublicId(managerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        Relation relation = relationRepository.findByManagerAndCustomer(manager, customer)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        RelationResponse response = RelationResponse.from(relation);
        return response;
    }

    @Override
    public List<RelationResponse> findAllRelationsByManager(UserPrincipal userDetails) {
        User manager = userRepository.findByAccountId(userDetails.getAccountId())
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        List<RelationResponse> responses = relationRepository.findAllByManager(manager).stream()
                .map(RelationResponse::from)
                .toList();
        return responses;
    }

    @Override
    public List<RelationResponse> findAllRelationsByCustomer(UUID customerPublicId, UserPrincipal userDetails) {
        if (!userDetails.getPublicId().equals(customerPublicId)) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        List<RelationResponse> responses = relationRepository.findAllByCustomer(customer).stream()
                .map(RelationResponse::from)
                .toList();
        return responses;
    }

    @Override
    @Transactional
    public RelationResponse updateRelationsStatus(UUID relationPublicId, RelationStatus newStatus, UserPrincipal userDetails) {
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!userDetails.getPublicId().equals(relation.getManager().getPublicId())) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        relation.updateStatus(newStatus);

        RelationResponse response = RelationResponse.from(relation);
        return response;
    }

    @Override
    @Transactional
    public void deleteRelation(UUID relationPublicId, UserPrincipal userDetails) {
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!userDetails.getPublicId().equals(relation.getManager().getPublicId())) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        relationRepository.delete(relation);
    }
}
