package carelog.carelog.auth.app.adapter.oauth.kakao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** refresh token을 의도적으로 모델링하지 않는 Kakao token 응답이다. */
public record KakaoTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") Long expiresIn
) {
}
