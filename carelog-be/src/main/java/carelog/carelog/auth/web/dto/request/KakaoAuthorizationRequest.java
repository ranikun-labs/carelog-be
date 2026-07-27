package carelog.carelog.auth.web.dto.request;

import carelog.carelog.auth.app.port.oauth.ClientChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Kakao Oauth 인가 시작 요청")
public record KakaoAuthorizationRequest (
    @Schema(description = "OAuth 클라이언트 채널", example = "WEB")
    @NotNull(message = "OAuth 클라이언트 채널은 필수입니다.")
    ClientChannel clientChannel,

    @Schema(description = "로그인 완료 후 이동 경로", example = "/")
    @NotBlank(message = "로그인 완료 후 이동 경로는 필수입니다.")
    String returnTo
) {
}
