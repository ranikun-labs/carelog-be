package carelog.carelog.auth.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kakao OAuth 인가 시작 응답")
public record KakaoAuthorizationResponse(
    @Schema(description = "Kakao 인가 페이지 URL")
    String authorizationUrl
) {
}
