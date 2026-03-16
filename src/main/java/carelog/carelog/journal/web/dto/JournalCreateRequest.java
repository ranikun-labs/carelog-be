package carelog.carelog.journal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record JournalCreateRequest(
        @Schema(description = "템플릿 ID (자유 양식이면 null)")
        UUID templatePublicId,

        @NotNull(message = "일지 내용은 필수입니다")
        @Schema(description = "일지 내용")
        Map<String, Object> content
) {}

