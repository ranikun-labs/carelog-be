package carelog.carelog.auth.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kakao OAuth 로그인 성공 응답")
public record KakaoExchangeResponse(
    @Schema(description = "Carelog Access Token")
    String accessToken,

    @Schema(description = "Carelog Refresh Token")
    String refreshToken
) {
}
