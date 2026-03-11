package carelog.carelog.auth.app;


import carelog.carelog.auth.web.dto.request.LoginRequest;
import carelog.carelog.auth.web.dto.request.TokenRefreshRequest;
import carelog.carelog.auth.web.dto.response.LoginResponse;
import carelog.carelog.auth.web.dto.response.TokenRefreshResponse;

public interface AuthService {

    /** 로그인 */
    LoginResponse login(LoginRequest request);

    /** 로그아웃 */
    void logout(String userId);

    /** 토큰 갱신 */
    TokenRefreshResponse refreshToken(TokenRefreshRequest request);
}
