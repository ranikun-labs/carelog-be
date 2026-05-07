package carelog.carelog.journal.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.journal.domain.JournalTemplate
import carelog.carelog.journal.domain.JournalTemplateRepository
import carelog.carelog.journal.domain.JournalTemplateStatus
import carelog.carelog.journal.web.dto.JournalTemplateCreateRequest
import carelog.carelog.journal.web.dto.JournalTemplateResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class JournalTemplateServiceImpl(
    private val journalTemplateRepository: JournalTemplateRepository,
) : JournalTemplateService {

    @Transactional
    override fun createTemplate(request: JournalTemplateCreateRequest, userDetails: CustomUserDetails): JournalTemplateResponse {
        val template = JournalTemplate.create(request.name, request.fields)
        template.assignOrganization(userDetails.organizationId)
        return JournalTemplateResponse.from(journalTemplateRepository.save(template))
    }

    override fun findAllTemplates(): List<JournalTemplateResponse> =
        journalTemplateRepository.findAllByStatus(JournalTemplateStatus.ACTIVE)
            .map(JournalTemplateResponse::from)
}
