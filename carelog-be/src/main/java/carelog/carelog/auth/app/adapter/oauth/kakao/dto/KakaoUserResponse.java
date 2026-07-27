package carelog.carelog.auth.app.adapter.oauth.kakao.dto;

/** 개인정보를 파싱하지 않기 위해 Kakao 사용자 식별자만 모델링한다. */
public record KakaoUserResponse(Long id) {
}
