package carelog.carelog.journal.web.dto;

import carelog.carelog.journal.domain.JournalStatus;
import carelog.carelog.journal.domain.RelationJournal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

public record JournalResponse(
        @Schema(description = "일지 공개 ID")
        UUID publicId,

        @Schema(description = "관계 공개 ID")
        UUID relationPublicId,

        @Schema(description = "템플릿 공개 ID (자유 양식이면 null)")
        UUID templatePublicId,

        @Schema(description = "일지 제목")
        String title,

        @Schema(description = "방문일")
        LocalDate visitDate,

        @Schema(description = "업무 데이터 — 업종별 동적 구조, AI O")
        Map<String, Object> caseData,

        @Schema(description = "개인 식별 정보 (PII) — 내부 전용, AI X")
        Map<String, Object> privateData,

        @Schema(description = "일지 상태")
        JournalStatus status,

        @Schema(description = "이전 버전 ID")
        Long previousId,

        @Schema(description = "생성 시각")
        OffsetDateTime createdAt
) {
    public static JournalResponse from(RelationJournal journal) {
        return new JournalResponse(
                journal.getPublicId(),
                journal.getRelation().getPublicId(),
                journal.getTemplate() != null ? journal.getTemplate().getPublicId() : null,
                journal.getTitle(),
                journal.getVisitDate(),
                journal.getCaseData(),
                journal.getPrivateData(),
                journal.getStatus(),
                journal.getPreviousId(),
                journal.getCreatedAt()
        );
    }
}

