package carelog.carelog.relation.app;

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
    public Relation createRelation(User manger, User customer) {
        return null;
    }

    @Override
    public Optional<Relation> findRelationById(Long relationId) {
        return Optional.empty();
    }

    @Override
    public Optional<Relation> findRelationByManagerAndCustomer(User manager, User customer) {
        return Optional.empty();
    }

    @Override
    public List<Relation> findAllRelationsByManager(User manager) {
        return List.of();
    }

    @Override
    public List<Relation> findAllRelationsByCustomer(User customer) {
        return List.of();
    }

    @Override
    public Relation updateRelationsStatus(Long relationId, RelationStatus newStatus) {
        return null;
    }

    @Override
    public void deleteRelation(Long relationId) {

    }
}