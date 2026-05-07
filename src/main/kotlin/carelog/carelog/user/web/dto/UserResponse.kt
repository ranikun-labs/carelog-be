package carelog.carelog.user.web.dto

import carelog.carelog.user.domain.ManagerType
import carelog.carelog.user.domain.User
import carelog.carelog.user.domain.UserRole
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "사용자 응답")
data class UserResponse(
    @Schema(description = "사용자 공개 ID") val publicId: UUID,
    @Schema(description = "사용자 로그인 ID") val userId: String?,
    @Schema(description = "이메일") val email: String?,
    @Schema(description = "이름") val name: String,
    @Schema(description = "역할") val role: UserRole,
    @Schema(description = "직군 (매니저만 해당)") val managerType: ManagerType?,
    @Schema(description = "암호화된 전화번호") val phoneEncrypted: String?,
    @Schema(description = "암호화된 주소") val addressEncrypted: String?,
) {
    companion object {
        fun from(user: User) = UserResponse(
            publicId = user.publicId,
            userId = user.userId,
            email = user.email,
            name = user.name,
            role = user.role,
            managerType = user.managerType,
            phoneEncrypted = user.phoneEncrypted,
            addressEncrypted = user.addressEncrypted,
        )
    }
}
