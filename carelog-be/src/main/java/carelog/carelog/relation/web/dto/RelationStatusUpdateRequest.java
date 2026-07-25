package carelog.carelog.relation.web.dto;

import carelog.carelog.relation.domain.RelationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RelationStatusUpdateRequest(
        @Schema(description = "변경할 관계 상태")
        @NotNull(message = "상태는 필수 입력 값입니다.")
        RelationStatus status
) {}
