package carelog.carelog.relation.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.relation.domain.*;
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

    @Override
    @Transactional
    public Relation createRelation(User manger, User customer) {
        if (relationRepository.existsByManagerAndCustomer(manger, customer)) {
            throw new CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS);
        }
        Relation relation = Relation.create(manger, customer);
        return relationRepository.save(relation);
    }

    @Override
    public Optional<Relation> findRelationById(Long relationId) {
        return relationRepository.findById(relationId);
    }

    @Override
    public Optional<Relation> findRelationByManagerAndCustomer(User manager, User customer) {
        return relationRepository.findByManagerAndCustomer(manager, customer);
    }

    @Override
    public List<Relation> findAllRelationsByManager(User manager) {
        return relationRepository.findAllByManager(manager);
    }

    @Override
    public List<Relation> findAllRelationsByCustomer(User customer) {
        return relationRepository.findAllByCustomer(customer);
    }

    @Override
    public Relation updateRelationsStatus(Long relationId, RelationStatus newStatus) {
        Relation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.RELATION_NOT_FOUND));
        relation.updateStatus(newStatus);
        return relation;
    }

    @Override
    @Transactional
    public void deleteRelation(Long relationId) {
        relationRepository.deleteById(relationId);;
    }
}