package carelog.carelog.journal.domain

import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import carelog.carelog.relation.domain.Relation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface RelationJournalRepository : JpaRepository<RelationJournal, Long> {
    fun findAllByRelationAndStatus(relation: Relation, status: JournalStatus): List<RelationJournal>
    fun findByPublicIdAndStatus(publicId: UUID, status: JournalStatus): Optional<RelationJournal>

    override fun deleteAll() { throw CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED) }
    override fun deleteAll(entities: Iterable<RelationJournal>) { throw CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED) }
    override fun deleteAllById(ids: Iterable<Long>) { throw CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED) }
    override fun deleteAllInBatch() { throw CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED) }
    override fun deleteAllInBatch(entities: Iterable<RelationJournal>) { throw CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED) }
    override fun deleteAllByIdInBatch(ids: Iterable<Long>) { throw CustomException(ExceptionStatus.JOURNAL_DELETE_NOT_ALLOWED) }
}
