package carelog.carelog.user.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Schema(description = "비밀번호")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요.")
        String password,

        @Schema(description = "암호화된 전화번호") String phoneEncrypted,
        @Schema(description = "암호화된 주소") String addressEncrypted
) {}
