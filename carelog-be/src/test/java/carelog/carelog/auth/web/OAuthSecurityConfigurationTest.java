package carelog.carelog.auth.web;

import carelog.carelog.auth.app.AuthService;
import carelog.carelog.auth.web.dto.response.LoginResponse;
import carelog.carelog.auth.app.oauth.OAuthAuthorizationService;
import carelog.carelog.auth.app.oauth.OAuthLoginService;
import carelog.carelog.auth.app.port.AuthTokenBundle;
import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.common.config.ClockConfig;
import carelog.carelog.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gateway를 통과한 로그인 전 OAuth 요청의 SecurityFilterChain 계약을 검증한다. */
@WebMvcTest(
        controllers = AuthController.class,
        properties = "gateway.internal-secret=test-gateway-internal-secret"
)
@Import({SecurityConfig.class, ClockConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class OAuthSecurityConfigurationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private OAuthAuthorizationService authorizationService;
    @MockitoBean private OAuthLoginService loginService;

    @Test
    @DisplayName("유효한 Gateway 헤더가 있는 로그인 전 Kakao authorization 요청은 JWT 없이도 통과한다")
    void kakaoAuthorization_allowsGatewayRequestWithoutJwt() throws Exception {
        when(authorizationService.startAuthorization(any())).thenReturn(
                new OAuthAuthorizationService.AuthorizationUrlResult(
                        URI.create("https://kauth.kakao.com/oauth/authorize?state=server-state"),
                        "server-state"
                )
        );

        mockMvc.perform(post("/auth/oauth/kakao/authorization")
                        .header("X-Gateway-Secret", "test-gateway-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientChannel":"WEB","returnTo":"/"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("유효한 Gateway 헤더가 있는 로그인 전 Kakao exchange 요청은 JWT 없이도 통과한다")
    void kakaoExchange_allowsGatewayRequestWithoutJwt() throws Exception {
        when(loginService.completeLogin(any())).thenReturn(
                new OAuthLoginResult.ExistingAccountAuthenticated(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        new AuthTokenBundle("access-token", "refresh-token")
                )
        );

        mockMvc.perform(post("/auth/oauth/kakao/exchange")
                        .header("X-Gateway-Secret", "test-gateway-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"authorization-code","state":"callback-state"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET Kakao authorization 경로는 로그인 전 공개되지 않는다")
    void kakaoAuthorization_getIsNotPublic() throws Exception {
        mockMvc.perform(get("/auth/oauth/kakao/authorization")
                        .header("X-Gateway-Secret", "test-gateway-internal-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT Kakao exchange 경로는 로그인 전 공개되지 않는다")
    void kakaoExchange_putIsNotPublic() throws Exception {
        mockMvc.perform(put("/auth/oauth/kakao/exchange")
                        .header("X-Gateway-Secret", "test-gateway-internal-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kakao OAuth 하위 유사 경로는 로그인 전 공개되지 않는다")
    void kakaoOtherPath_isNotPublic() throws Exception {
        mockMvc.perform(post("/auth/oauth/kakao/other")
                        .header("X-Gateway-Secret", "test-gateway-internal-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("기존 password 로그인 경로는 로그인 전 공개 상태를 유지한다")
    void passwordLogin_remainsPublic() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("access-token", "refresh-token"));

        mockMvc.perform(post("/auth/login")
                        .header("X-Gateway-Secret", "test-gateway-internal-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"manager","password":"password123"}
                                """))
                .andExpect(status().isOk());
    }
}
