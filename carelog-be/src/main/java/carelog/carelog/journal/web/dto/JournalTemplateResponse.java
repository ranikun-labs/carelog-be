package carelog.carelog.journal.web.dto;

import carelog.carelog.journal.domain.JournalTemplate;
import carelog.carelog.journal.domain.JournalTemplateStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.*;

public record JournalTemplateResponse(
        @Schema(description = "템플릿 공개 ID")
        UUID publicId,

        @Schema(description = "템플릿 이름")
        String name,

        @Schema(description = "템플릿 필드 목록")
        List<Map<String, Object>> fields,

        @Schema(description = "템플릿 상태")
        JournalTemplateStatus status
) {
    public static JournalTemplateResponse from(JournalTemplate template) {
        return new JournalTemplateResponse(
                template.getPublicId(),
                template.getName(),
                template.getFields(),
                template.getStatus()
        );
    }
}
