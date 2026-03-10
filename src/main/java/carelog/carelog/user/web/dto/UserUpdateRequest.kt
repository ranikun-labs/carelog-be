package carelog.carelog.user.web.dto

import jakarta.validation.constraints.Size


data class UserUpdateRequest(
    @field:Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
    val password: String? = null,

    val phoneEncrypted: String? = null,
    val addressEncrypted: String? = null
)
