package carelog.carelog.journal.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.common.web.exception.CustomException
import carelog.carelog.common.web.exception.ExceptionStatus
import carelog.carelog.journal.domain.*
import carelog.carelog.journal.web.dto.JournalCreateRequest
import carelog.carelog.journal.web.dto.JournalResponse
import carelog.carelog.relation.domain.Relation
import carelog.carelog.relation.domain.RelationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class JournalServiceImpl(
    private val journalRepository: RelationJournalRepository,
    private val templateRepository: JournalTemplateRepository,
    private val relationRepository: RelationRepository,
) : JournalService {

    private fun findRelationAndCheckOwnership(relationPublicId: UUID, userDetails: CustomUserDetails): Relation {
        val relation = relationRepository.findByPublicId(relationPublicId)
            .orElseThrow { CustomException(ExceptionStatus.RELATION_NOT_FOUND) }
        if (userDetails.publicId != relation.manager.publicId) {
            throw CustomException(ExceptionStatus.ACCESS_DENIED)
        }
        return relation
    }

    @Transactional
    override fun createJournal(relationPublicId: UUID, request: JournalCreateRequest, userDetails: CustomUserDetails): JournalResponse {
        val relation = findRelationAndCheckOwnership(relationPublicId, userDetails)

        val template = request.templatePublicId?.let {
            templateRepository.findByPublicId(it)
                .orElseThrow { CustomException(ExceptionStatus.JOURNAL_TEMPLATE_NOT_FOUND) }
        }

        val journal = RelationJournal.create(
            relation, template,
            request.title, request.visitDate,
            request.caseData, request.privateData,
        )
        journal.assignOrganization(userDetails.organizationId)

        return JournalResponse.from(journalRepository.save(journal))
    }

    @Transactional
    override fun updateJournal(relationPublicId: UUID, journalPublicId: UUID, request: JournalCreateRequest, userDetails: CustomUserDetails): JournalResponse {
        findRelationAndCheckOwnership(relationPublicId, userDetails)

        val existing = journalRepository.findByPublicIdAndStatus(journalPublicId, JournalStatus.ACTIVE)
            .orElseThrow { CustomException(ExceptionStatus.JOURNAL_NOT_FOUND) }

        val template = request.templatePublicId?.let {
            templateRepository.findByPublicId(it)
                .orElseThrow { CustomException(ExceptionStatus.JOURNAL_TEMPLATE_NOT_FOUND) }
        }

        existing.supersede()

        val revision = RelationJournal.createAsRevision(
            existing.relation, template,
            request.title, request.visitDate,
            request.caseData, request.privateData,
            existing.id,
        )
        revision.assignOrganization(userDetails.organizationId)

        return JournalResponse.from(journalRepository.save(revision))
    }

    override fun findAllJournals(relationPublicId: UUID, userDetails: CustomUserDetails): List<JournalResponse> {
        val relation = findRelationAndCheckOwnership(relationPublicId, userDetails)
        return journalRepository.findAllByRelationAndStatus(relation, JournalStatus.ACTIVE)
            .map(JournalResponse::from)
    }

    override fun findJournal(relationPublicId: UUID, journalPublicId: UUID, userDetails: CustomUserDetails): JournalResponse {
        findRelationAndCheckOwnership(relationPublicId, userDetails)
        return journalRepository.findByPublicIdAndStatus(journalPublicId, JournalStatus.ACTIVE)
            .map(JournalResponse::from)
            .orElseThrow { CustomException(ExceptionStatus.JOURNAL_NOT_FOUND) }
    }

    override fun findJournalHistory(relationPublicId: UUID, journalPublicId: UUID, userDetails: CustomUserDetails): List<JournalResponse> {
        findRelationAndCheckOwnership(relationPublicId, userDetails)

        val latest = journalRepository.findByPublicIdAndStatus(journalPublicId, JournalStatus.ACTIVE)
            .orElseThrow { CustomException(ExceptionStatus.JOURNAL_NOT_FOUND) }

        // previousId 체인 추적 — MVP 단계, 병목 측정 후 Recursive CTE로 전환
        val history = mutableListOf<RelationJournal>()
        var current: RelationJournal? = latest
        while (current != null) {
            history.add(current)
            current = current.previousId?.let { journalRepository.findById(it).orElse(null) }
        }

        return history.map(JournalResponse::from)
    }
}
