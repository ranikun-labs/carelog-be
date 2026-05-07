package carelog.carelog.journal.web.dto

import carelog.carelog.journal.domain.JournalTemplate
import carelog.carelog.journal.domain.JournalTemplateStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class JournalTemplateResponse(
    @Schema(description = "템플릿 공개 ID") val publicId: UUID,
    @Schema(description = "템플릿 이름") val name: String,
    @Schema(description = "템플릿 필드 목록") val fields: List<Map<String, Any>>,
    @Schema(description = "템플릿 상태") val status: JournalTemplateStatus,
) {
    companion object {
        fun from(template: JournalTemplate) = JournalTemplateResponse(
            publicId = template.publicId,
            name = template.name,
            fields = template.fields,
            status = template.status,
        )
    }
}
