package carelog.carelog.journal.app

import carelog.carelog.auth.app.CustomUserDetails
import carelog.carelog.journal.web.dto.JournalTemplateCreateRequest
import carelog.carelog.journal.web.dto.JournalTemplateResponse

interface JournalTemplateService {
    fun createTemplate(request: JournalTemplateCreateRequest, userDetails: CustomUserDetails): JournalTemplateResponse
    fun findAllTemplates(): List<JournalTemplateResponse>
}
