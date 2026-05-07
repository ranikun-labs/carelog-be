package carelog.carelog.journal.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.UUID

data class JournalCreateRequest(
    @Schema(description = "템플릿 ID (자유 양식이면 null)")
    val templatePublicId: UUID? = null,

    @field:NotBlank(message = "제목은 필수입니다")
    @Schema(description = "일지 제목", example = "1회차 방문")
    val title: String,

    @field:NotNull(message = "방문일은 필수입니다")
    @Schema(description = "방문일 (예: 2026-03-16)", example = "2026-03-16")
    val visitDate: LocalDate,

    @field:NotNull(message = "업무 데이터는 필수입니다")
    @Schema(description = "업무 데이터 — 업종별 동적 구조, AI 파이프라인 전달 대상")
    val caseData: Map<String, Any>,

    @Schema(description = "개인 식별 정보 (PII) — 내부 전용, AI 파이프라인 진입 불가 (null 허용)")
    val privateData: Map<String, Any>? = null,
)
