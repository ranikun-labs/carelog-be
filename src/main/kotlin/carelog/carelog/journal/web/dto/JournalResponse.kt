package carelog.carelog.journal.web.dto

import carelog.carelog.journal.domain.JournalStatus
import carelog.carelog.journal.domain.RelationJournal
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class JournalResponse(
    @Schema(description = "일지 공개 ID") val publicId: UUID,
    @Schema(description = "관계 공개 ID") val relationPublicId: UUID,
    @Schema(description = "템플릿 공개 ID (자유 양식이면 null)") val templatePublicId: UUID?,
    @Schema(description = "일지 제목") val title: String,
    @Schema(description = "방문일") val visitDate: LocalDate,
    @Schema(description = "업무 데이터 — 업종별 동적 구조, AI O") val caseData: Map<String, Any>,
    @Schema(description = "개인 식별 정보 (PII) — 내부 전용, AI X") val privateData: Map<String, Any>?,
    @Schema(description = "일지 상태") val status: JournalStatus,
    @Schema(description = "이전 버전 ID") val previousId: Long?,
    @Schema(description = "생성 시각") val createdAt: OffsetDateTime?,
) {
    companion object {
        fun from(journal: RelationJournal) = JournalResponse(
            publicId = journal.publicId,
            relationPublicId = journal.relation.publicId,
            templatePublicId = journal.template?.publicId,
            title = journal.title,
            visitDate = journal.visitDate,
            caseData = journal.caseData,
            privateData = journal.privateData,
            status = journal.status,
            previousId = journal.previousId,
            createdAt = journal.createdAt,
        )
    }
}
