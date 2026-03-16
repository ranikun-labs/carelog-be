package carelog.carelog.journal.web.dto;

import carelog.carelog.journal.domain.JournalStatus;
import carelog.carelog.journal.domain.RelationJournal;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.*;

public record JournalResponse(
        @Schema(description = "일지 공개 ID")
        UUID publicId,

        @Schema(description = "관계 공개 ID")
        UUID relationPublicId,

        @Schema(description = "템플릿 공개 ID (자유 양식이면 null)")
        UUID templatePublicId,

        @Schema(description = "일지 내용")
        Map<String, Object> content,

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
                journal.getContent(),
                journal.getStatus(),
                journal.getPreviousId(),
                journal.getCreatedAt()
        );
    }
}

