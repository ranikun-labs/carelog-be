package carelog.carelog.journal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record JournalTemplateCreateRequest(
        @NotBlank(message = "템플릿 이름은 필수입니다")
        @Schema(description = "템플릿 이름", example = "기본 진료 양식")
        String name,

        @NotNull(message = "필드 목록은 필수입니다")
        @Schema(description = "템플릿 필드 목록")
        @NotNull List<Map<String, Object>> fields
){}
