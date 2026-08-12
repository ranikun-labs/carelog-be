package carelog.carelog.customer.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerCreateRequest(
        @Schema(description = "고객 표시 이름")
        @NotBlank(message = "고객 표시 이름은 필수 입력 값입니다.")
        String displayName,

        @Schema(description = "고객 메모")
        String customerMemo
) {
}
