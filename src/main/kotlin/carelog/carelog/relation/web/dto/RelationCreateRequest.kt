package carelog.carelog.relation.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class RelationCreateRequest(
    @field:NotNull(message = "고객 PublicId는 필수 입력 값입니다.")
    @Schema(description = "고객 공개 ID")
    val customerPublicId: UUID,
)
