package carelog.carelog.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

@Schema(description = "고객 생성 요청")
data class CustomerCreateRequest(
    @field:NotBlank(message = "이름은 필수 입력 값입니다.")
    @Schema(description = "고객 이름")
    val name: String,
)
