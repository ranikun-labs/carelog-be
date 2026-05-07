package carelog.carelog.user.web.dto

import carelog.carelog.user.domain.ManagerType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "매니저 생성 요청")
data class ManagerCreateRequest(
    @field:NotBlank(message = "사용자의 ID는 필수 입력 값입니다.")
    @field:Size(min = 4, max = 20, message = "사용자 ID는 4자 이상 20이하로 입력해주세요.")
    @Schema(description = "로그인 ID")
    val userId: String,

    @field:NotBlank(message = "이메일은 필수 입력 값입니다.")
    @field:Email(message = "유효한 이메일 형식이 아닙니다.")
    @Schema(description = "이메일")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @field:Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
    @Schema(description = "비밀번호")
    val password: String,

    @field:NotBlank(message = "이름 필수 입력 값입니다.")
    @Schema(description = "이름")
    val name: String,

    @field:NotNull(message = "직군은 필수 입력 값입니다.")
    @Schema(description = "직군")
    val managerType: ManagerType,

    @Schema(description = "암호화된 전화번호")
    val phoneEncrypted: String? = null,

    @Schema(description = "암호화된 주소")
    val addressEncrypted: String? = null,
)
