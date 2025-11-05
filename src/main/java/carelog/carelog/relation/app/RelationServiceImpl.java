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

    private User findUserByIdForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public Relation createRelation(Long managerId, Long customerId) {
        // 1. User 조회 (락 없음 - 성능 최적화)
        // User 테이블은 병목이 되기 쉬우므로, 여기서는 락을 걸지 않습니다.
        User manager = findUserById(managerId);
        User customer = findUserById(customerId);

        // 2. 역할 선검증 (Fail Fast)
        if (manager.getRole() != UserRole.MANAGER || customer.getRole() != UserRole.CUSTOMER) {
            throw new CustomException(ExceptionStatus.INVALID_USER_ROLE);
        }

        // 3. 동시성 제어 (Pessimistic Lock) ★★★
        // DB의 유니크 제약(Unique Constraint)을 대신하는 새로운 방어막입니다.
        // 'Relation' 테이블의 (manager, customer) 조합 자리에 "쓰기 락"을 겁니다.
        // 다른 스레드가 이 쿼리를 만나면, 현재 스레드가 끝날 때까지 대기합니다.
        relationRepository.findByManagerAndCustomerForUpdate(manager, customer)
                .ifPresent(r -> {
                    // (@SQLRestriction 덕분에) "활성화된" 중복 관계가 이미 존재한다는 뜻
                    throw new CustomException(ExceptionStatus.RELATION_ALREADY_EXISTS);
                });

        // 4. 생성
        // 3번 락이 통과되었다면, 이제 이 조합은 '내 차지'이므로 안전합니다.
        // DB 제약이 없으므로, 이제 'try-catch' 블록은 필요 없습니다.
        Relation relation = Relation.create(manager, customer);
        return relationRepository.save(relation);
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