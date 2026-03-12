package carelog.carelog.relation.app;

import carelog.carelog.auth.app.CustomUserDetails;
import carelog.carelog.common.web.exception.*;
import carelog.carelog.relation.domain.*;
import carelog.carelog.relation.web.dto.*;
import carelog.carelog.user.domain.*;
import lombok.*;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private CustomUserDetails getCurrentUserDetails() {
        return (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
    }

    @Override
    @Transactional
    public RelationResponse createRelation(UUID customerPublicId) {
        CustomUserDetails userDetails = getCurrentUserDetails();
        User manager = userRepository.findByUserId(userDetails.getUsername())
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        if (manager.getRole() != UserRole.MANAGER || customer.getRole() != UserRole.CUSTOMER)
        { throw new CustomException(ExceptionStatus.INVALID_USER_ROLE); }

        relationRepository.findByManagerAndCustomerForUpdate(manager, customer)
                .ifPresent(r ->
                { throw new CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS); });

        Relation relation = Relation.create(manager, customer);
        relation.assignOrganization(userDetails.getOrganizationId());

        return RelationResponse.from(relationRepository.save(relation));
    }

    @Override
    public RelationResponse findRelationByPublicId(UUID relationPublicId) {
        UUID currentPublicId = getCurrentUserDetails().getPublicId();
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!currentPublicId.equals(relation.getManager().getPublicId()) &&
                ! currentPublicId.equals(relation.getCustomer().getPublicId())
        ) { throw new CustomException(ExceptionStatus.ACCESS_DENIED); }

        return RelationResponse.from(relation);
    }

    @Override
    public RelationResponse findRelationByManagerAndCustomer(UUID managerPublicId, UUID customerPublicId) {
        UUID currentPublicId = getCurrentUserDetails().getPublicId();
        if (!currentPublicId.equals(managerPublicId) &&
                !currentPublicId.equals(customerPublicId)
        ) { throw new CustomException(ExceptionStatus.ACCESS_DENIED); }

        User manager = userRepository.findByPublicId(managerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
        Relation relation = relationRepository.findByManagerAndCustomer(manager, customer)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        return RelationResponse.from(relation);
    }

    @Override
    public List<RelationResponse> findAllRelationsByManager() {
        CustomUserDetails userDetails = getCurrentUserDetails();
        User manager = userRepository.findByUserId(userDetails.getUsername())
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        return relationRepository.findAllByManager(manager).stream()
                .map(RelationResponse::from)
                .toList();
    }

    @Override
    public List<RelationResponse> findAllRelationsByCustomer(UUID customerPublicId) {
        UUID currentPublicId = getCurrentUserDetails().getPublicId();
        if (!currentPublicId.equals(customerPublicId)) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        User customer = userRepository.findByPublicId(customerPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        return relationRepository.findAllByCustomer(customer).stream()
                .map(RelationResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public RelationResponse updateRelationsStatus(UUID relationPublicId, RelationStatus newStatus) {
        UUID currentPublicId = getCurrentUserDetails().getPublicId();
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!currentPublicId.equals(relation.getManager().getPublicId())) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        relation.updateStatus(newStatus);
        return RelationResponse.from(relation);
    }

    @Override
    @Transactional
    public void deleteRelation(UUID relationPublicId) {
        UUID currentPublicId = getCurrentUserDetails().getPublicId();
        Relation relation = relationRepository.findByPublicId(relationPublicId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        if (!currentPublicId.equals(relation.getManager().getPublicId())) {
            throw new CustomException(ExceptionStatus.ACCESS_DENIED);
        }

        relationRepository.delete(relation);
    }
}