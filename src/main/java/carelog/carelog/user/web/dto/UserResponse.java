package carelog.carelog.user.web.dto;

import carelog.carelog.user.domain.ManagerType;
import carelog.carelog.user.domain.User;
import carelog.carelog.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record UserResponse(
        @Schema(description = "사용자 공개 ID") UUID publicId,
        @Schema(description = "사용자 로그인 ID") String userId,
        @Schema(description = "이메일") String email,
        @Schema(description = "이름") String name,
        @Schema(description = "역할") UserRole role,
        @Schema(description = "직군 (매니저만 해당)") ManagerType managerType,
        @Schema(description = "암호화된 전화번호") String phoneEncrypted,
        @Schema(description = "암호화된 주소") String addressEncrypted
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getManagerType(),
                user.getPhoneEncrypted(),
                user.getAddressEncrypted()
        );
    }
}