package carelog.carelog.auth.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Kakao OAuth 인가 코드 교환 요청")
public record KakaoExchangeRequest(
    @Schema(description = "Kakao가 전달한 인가 코드")
    @NotBlank(message = "인가 코드는 필수입니다.")
    String code,

    @Schema(description = "인가 시작 시 서버가 발급한  state")
    @NotBlank(message = "state는 필수입니다")
    String state
) {
    @Override
    public String toString() {
        return "KakaoExchangeRequest[code=REDACTED, state=REDACTED]";
    }
}
