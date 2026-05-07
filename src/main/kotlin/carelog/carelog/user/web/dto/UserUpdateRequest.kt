package carelog.carelog.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

@Schema(description = "사용자 수정 요청")
data class UserUpdateRequest(
    @field:Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
    @Schema(description = "비밀번호")
    val password: String? = null,

    @Schema(description = "암호화된 전화번호")
    val phoneEncrypted: String? = null,

    @Schema(description = "암호화된 주소")
    val addressEncrypted: String? = null,
)
