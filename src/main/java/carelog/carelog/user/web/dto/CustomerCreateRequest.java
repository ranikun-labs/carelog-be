package carelog.carelog.user.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CustomerCreateRequest(
        @Schema(description = "고객 이름")
        @NotBlank(message = "이름은 필수 입력 값입니다.")
        String name
) {}