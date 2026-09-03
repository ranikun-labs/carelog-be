package carelog.carelog.auth.web;

import carelog.carelog.auth.app.AuthService;
import carelog.carelog.auth.app.oauth.OAuthAuthorizationService;
import carelog.carelog.auth.app.oauth.OAuthLoginService;
import carelog.carelog.auth.app.port.AuthTokenBundle;
import carelog.carelog.auth.app.port.oauth.ClientChannel;
import carelog.carelog.auth.app.port.oauth.OnboardingCandidate;
import carelog.carelog.auth.app.port.oauth.OAuthAuthorizationCommand;
import carelog.carelog.auth.app.port.oauth.OAuthCallbackCommand;
import carelog.carelog.auth.app.port.oauth.OAuthLoginResult;
import carelog.carelog.auth.app.port.oauth.OAuthStateStoreUnavailableException;
import carelog.carelog.auth.web.dto.request.KakaoExchangeRequest;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.common.web.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** OAuth Core 결과를 외부 HTTP 계약으로 안전하게 변환하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private OAuthAuthorizationService authorizationService;
    @Mock private OAuthLoginService loginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, authorizationService, loginService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @DisplayName("Kakao authorization 시작은 authorizationUrl만 반환하고 state를 별도 노출하지 않는다")
    @Test
    void startKakaoAuthorization_returnsOnlyAuthorizationUrl() throws Exception {
        String authorizationUrl = "https://kauth.kakao.com/oauth/authorize?state=server-generated-state";
        when(authorizationService.startAuthorization(any())).thenReturn(
                new OAuthAuthorizationService.AuthorizationUrlResult(
                        URI.create(authorizationUrl),
                        "server-generated-state"
                )
        );

        mockMvc.perform(post("/api/v1/auth/oauth/kakao/authorization")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientChannel":"WEB","returnTo":"/journals/42"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").value(authorizationUrl))
                .andExpect(jsonPath("$.data.state").doesNotExist())
                .andExpect(jsonPath("$.data.codeVerifier").doesNotExist())
                .andExpect(jsonPath("$.data.nonce").doesNotExist());

        ArgumentCaptor<OAuthAuthorizationCommand> commandCaptor =
                ArgumentCaptor.forClass(OAuthAuthorizationCommand.class);
        verify(authorizationService).startAuthorization(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(
                new OAuthAuthorizationCommand("kakao", ClientChannel.WEB, "/journals/42")
        );
    }

    @DisplayName("기존 MOBILE Kakao authorization 요청은 공개 DTO 변경 없이 전달한다")
    @Test
    void startKakaoAuthorization_preservesLegacyMobileContract() throws Exception {
        when(authorizationService.startAuthorization(any())).thenReturn(
                new OAuthAuthorizationService.AuthorizationUrlResult(
                        URI.create("https://kauth.kakao.com/oauth/authorize?state=mobile-state"),
                        "mobile-state"
                )
        );

        mockMvc.perform(post("/api/v1/auth/oauth/kakao/authorization")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientChannel":"MOBILE","returnTo":"/journals/42"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizationUrl").value(
                        "https://kauth.kakao.com/oauth/authorize?state=mobile-state"));

        ArgumentCaptor<OAuthAuthorizationCommand> commandCaptor =
                ArgumentCaptor.forClass(OAuthAuthorizationCommand.class);
        verify(authorizationService).startAuthorization(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(
                new OAuthAuthorizationCommand("kakao", ClientChannel.MOBILE, "/journals/42")
        );
    }

    @DisplayName("Kakao authorization 요청에서 returnTo가 누락되면 400으로 거부한다")
    @Test
    void startKakaoAuthorization_missingReturnTo_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/authorization")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientChannel":"WEB"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authorizationService);
    }

    @DisplayName("Kakao authorization 요청에서 returnTo가 null이면 400으로 거부한다")
    @Test
    void startKakaoAuthorization_nullReturnTo_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/authorization")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientChannel":"WEB","returnTo":null}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authorizationService);
    }

    @DisplayName("Kakao authorization 요청에서 returnTo가 blank이면 400으로 거부한다")
    @Test
    void startKakaoAuthorization_blankReturnTo_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/kakao/authorization")
                        .contextPath("/api/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientChannel":"WEB","returnTo":" "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authorizationService);
    }

    @DisplayName("연결된 Kakao 계정은 Carelog accessToken과 refreshToken만 반환한다")
    @Test
    void exchangeKakaoCode_existingAccount_returnsCarelogTokens() throws Exception {
        when(loginService.completeLogin(any())).thenReturn(
                new OAuthLoginResult.ExistingAccountAuthenticated(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        new AuthTokenBundle("carelog-access-token", "carelog-refresh-token")
                )
        );

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("carelog-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("carelog-refresh-token"))
                .andExpect(jsonPath("$.data.providerToken").doesNotExist());

        ArgumentCaptor<OAuthCallbackCommand> commandCaptor =
                ArgumentCaptor.forClass(OAuthCallbackCommand.class);
        verify(loginService).completeLogin(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(
                new OAuthCallbackCommand("kakao", "authorization-code", "callback-state")
        );
    }

    @DisplayName("신규 Kakao 계정은 onboarding 후보 정보를 노출하지 않고 account not linked로 처리한다")
    @Test
    void exchangeKakaoCode_newAccount_returnsAccountNotLinked() throws Exception {
        when(loginService.completeLogin(any())).thenReturn(
                new OAuthLoginResult.NewAccountOnboardingRequired(new OnboardingCandidate(
                        "kakao", "provider-subject", "user@example.com", true, "홍길동", "/journals/42"
                ))
        );

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("연결된 Carelog 계정이 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.data.providerSubject").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.displayNameHint").doesNotExist());
    }

    @DisplayName("비활성 또는 고아 외부 신원은 동일한 identity conflict 계약으로 처리한다")
    @ParameterizedTest
    @EnumSource(OAuthLoginResult.ConflictReason.class)
    void exchangeKakaoCode_identityConflict_returnsSinglePublicConflict(
            OAuthLoginResult.ConflictReason reason
    ) throws Exception {
        when(loginService.completeLogin(any())).thenReturn(new OAuthLoginResult.ExternalIdentityConflict(reason));

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ExceptionStatus.OAUTH_IDENTITY_CONFLICT.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @DisplayName("Kakao provider 인증 실패는 내부 사유를 노출하지 않고 401로 처리한다")
    @Test
    void exchangeKakaoCode_providerAuthenticationFailure_returnsUnauthorized() throws Exception {
        when(loginService.completeLogin(any())).thenReturn(
                new OAuthLoginResult.ProviderAuthenticationFailed(
                        OAuthLoginResult.FailureReason.PROVIDER_UNAVAILABLE
                )
        );

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ExceptionStatus.OAUTH_PROVIDER_AUTH_FAILED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @DisplayName("만료 또는 불일치한 state는 401로 처리한다")
    @Test
    void exchangeKakaoCode_invalidOrExpiredState_returnsUnauthorized() throws Exception {
        when(loginService.completeLogin(any())).thenReturn(new OAuthLoginResult.InvalidOrExpiredState());

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ExceptionStatus.INVALID_OAUTH_STATE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @DisplayName("OAuth state 저장소 장애는 OAuth 실패로 바꾸지 않고 503으로 전파한다")
    @Test
    void exchangeKakaoCode_stateStoreUnavailable_returnsServiceUnavailable() throws Exception {
        when(loginService.completeLogin(any())).thenThrow(
                new OAuthStateStoreUnavailableException("state store unavailable", new RuntimeException("redis unavailable"))
        );

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(ExceptionStatus.OAUTH_STATE_STORE_UNAVAILABLE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @DisplayName("토큰 발급 등 내부 장애는 OAuth 인증 실패로 바꾸지 않고 기존 500 경계로 전파한다")
    @Test
    void exchangeKakaoCode_internalFailure_propagatesAsInternalServerError() throws Exception {
        when(loginService.completeLogin(any())).thenThrow(new IllegalStateException("token issuance unavailable"));

        mockMvc.perform(kakaoExchangeRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("서버에 예상치 못한 오류가 발생했습니다."));
    }

    @DisplayName("Kakao 교환 요청은 JSON 계약을 유지하면서 문자열 표현에서 code와 state를 숨긴다")
    @Test
    void kakaoExchangeRequest_redactsSensitiveValuesFromToString() throws Exception {
        String authorizationCode = "identifiable-authorization-code-7f42";
        String authorizationState = "identifiable-callback-state-9a31";

        KakaoExchangeRequest request = new ObjectMapper().readValue("""
                {
                  "code": "identifiable-authorization-code-7f42",
                  "state": "identifiable-callback-state-9a31"
                }
                """, KakaoExchangeRequest.class);

        assertThat(request.code()).isEqualTo(authorizationCode);
        assertThat(request.state()).isEqualTo(authorizationState);
        assertThat(request.toString())
                .doesNotContain(authorizationCode, authorizationState)
                .contains("code=REDACTED", "state=REDACTED");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder kakaoExchangeRequest() {
        return post("/api/v1/auth/oauth/kakao/exchange")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"authorization-code","state":"callback-state"}
                        """);
    }
}
