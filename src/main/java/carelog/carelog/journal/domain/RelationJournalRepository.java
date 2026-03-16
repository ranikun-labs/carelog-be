package carelog.carelog.journal.domain;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.relation.domain.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelationJournalRepository extends JpaRepository<RelationJournal, Long> {

    List<RelationJournal> findAllByRelationAndStatus(Relation relation, JournalStatus status);

    Optional<RelationJournal> findByPublicIdAndStatus(UUID publicId, JournalStatus status);

    // 이력 조회 - previousId 체인 추적용
    Optional<RelationJournal> findById(Long id);

    /**
     * Delete 차단
     */
    @Override
    default void deleteAll() {
        throw new CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED);
    }

    @Override
    default void deleteAll(Iterable<? extends RelationJournal> entities) {
        throw new CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED);
    }

    @Override
    default void deleteAllById(Iterable<? extends Long> ids) {
        throw new CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED);
    }

    @Override
    default void deleteAllInBatch() {
        throw new CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED);
    }

    @Override
    default void deleteAllInBatch(Iterable<RelationJournal> entities) {
        throw new CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED);
    }

    @Override
    default void deleteAllByIdInBatch(Iterable<Long> ids) {
        throw new CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED);
    }


}
