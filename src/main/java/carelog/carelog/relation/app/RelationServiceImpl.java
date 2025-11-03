package carelog.carelog.relation.app;

import carelog.carelog.common.web.exception.*;
import carelog.carelog.relation.domain.*;
import carelog.carelog.user.domain.*;
import lombok.*;
import org.springframework.dao.*;
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
    public Relation createRelation(Long managerId, Long customerId) {
        User manager = findUserById(managerId);
        User customer = findUserById(customerId);

        if (relationRepository.existsByManagerAndCustomer(manager, customer)) {
            throw new CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS);
        }

        Relation relation = Relation.create(manager, customer);

        try {
            return relationRepository.save(relation);
        } catch (DataIntegrityViolationException e) {
            // DB Unique 제약조건 위반 시 (동시성 문제)
            throw new CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS);
        }
    }

    @Override
    public Optional<Relation> findRelationById(Long relationId) {
        return relationRepository.findById(relationId);
    }

    @Override
    public Optional<Relation> findRelationByManagerAndCustomer(Long managerId, Long customerId) {
        User manager = findUserById(managerId);
        User customer = findUserById(customerId);
        return relationRepository.findByManagerAndCustomer(manager, customer);
    }

    @Override
    public List<Relation> findAllRelationsByManager(Long managerId) {
        User manager = findUserById(managerId);
        return relationRepository.findAllByManager(manager);
    }

    @Override
    public List<Relation> findAllRelationsByCustomer(Long customerId) {
        User customer = findUserById(customerId);
        return relationRepository.findAllByCustomer(customer);
    }


    @Override
    @Transactional
    public Relation updateRelationsStatus(Long relationId, RelationStatus newStatus) {
        Relation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));
        relation.updateStatus(newStatus);
        return relation;
    }

    @Override
    @Transactional
    public void deleteRelation(Long relationId) {
        Relation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));

        relationRepository.delete(relation);
    }
}