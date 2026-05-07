package carelog.carelog.journal.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class JournalTemplateCreateRequest(
    @field:NotBlank(message = "템플릿 이름은 필수입니다")
    @Schema(description = "템플릿 이름", example = "기본 진료 양식")
    val name: String,

    @field:NotNull(message = "필드 목록은 필수입니다")
    @Schema(description = "템플릿 필드 목록 — key(저장키), label(화면표시명), type(text/number/textarea), category(case/private)")
    val fields: List<Map<String, Any>>,
)
