package carelog.carelog.auth.app;


import carelog.carelog.auth.web.dto.request.*;
import carelog.carelog.auth.web.dto.response.*;

import java.util.UUID;

public interface AuthService {

    /**
     * 로그인
     */
    LoginResponse login(LoginRequest request);

    /**
     * 로그아웃
     */
    void logout(UUID accountId, String accessToken);

    /**
     * 토큰 갱신
     */
    TokenRefreshResponse refreshToken(TokenRefreshRequest request);
}
