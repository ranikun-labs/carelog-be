package carelog.carelog.user.web.dto;

import carelog.carelog.user.domain.ManagerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ManagerCreateRequest(
        @Schema(description = "로그인 ID")
        @NotBlank(message = "사용자의 ID는 필수 입력 값입니다.")
        @Size(min = 4, max = 20, message = "사용자 ID는 4자 이상 20이하로 입력해주세요.")
        String userId,

        @Schema(description = "이메일")
        @NotBlank(message = "이메일은 필수 입력 값입니다.")
        @Email(message = "유효한 이메일 형식이 아닙니다.")
        String email,

        @Schema(description = "비밀번호")
        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        String password,

        @Schema(description = "이름")
        @NotBlank(message = "이름 필수 입력 값입니다.")
        String name,

        @Schema(description = "직군")
        @NotNull(message = "직군은 필수 입력 값입니다.")
        ManagerType managerType,

        @Schema(description = "암호화된 전화번호") String phoneEncrypted,
        @Schema(description = "암호화된 주소") String addressEncrypted
) {}