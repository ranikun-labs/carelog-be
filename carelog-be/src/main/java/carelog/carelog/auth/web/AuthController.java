package carelog.carelog.auth.web;

import carelog.carelog.auth.app.*;
import carelog.carelog.auth.app.oauth.*;
import carelog.carelog.auth.app.port.oauth.*;
import carelog.carelog.auth.web.dto.request.*;
import carelog.carelog.auth.web.dto.response.*;
import carelog.carelog.common.web.dto.response.*;
import carelog.carelog.common.web.dto.response.ApiResponse;
import carelog.carelog.common.web.exception.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.*;
import jakarta.validation.*;
import lombok.*;
import org.springframework.http.*;
import org.springframework.security.core.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "인증", description = "로그인, 로그아웃, 토큰 갱신 API")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthAuthorizationService oAuthAuthorizationService;
    private final OAuthLoginService oAuthLoginService;

    @Operation(summary = "로그인", description = "사용자 ID와 비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 - 아이디 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ApiResponse.ok(response);
    }

    @Operation(summary = "로그아웃", description = "현재 로그인된 사용자의 Refresh Token을 삭제하여 로그아웃합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요 - 유효한 Access Token이 필요합니다.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) Authentication authentication,
            @RequestHeader("Authorization") String authHeader
    ) {
        // authentication.getName()은 Identity Foundation B0부터 accountId 문자열이다(과거 loginId 대체).
        UUID accountId = UUID.fromString(authentication.getName());
        if (!authHeader.startsWith("Bearer ")) {
            return ApiResponse.noContent();
        }
        String accessToken = authHeader.substring(7);
        authService.logout(accountId, accessToken);
        return ApiResponse.noContent();
    }

    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
                    content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh Token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Refresh Token을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        TokenRefreshResponse response = authService.refreshToken(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/oauth/kakao/authorization")
    public ResponseEntity<ApiResponse<KakaoAuthorizationResponse>> startKakaoAuthorization(
            @Valid @RequestBody KakaoAuthorizationRequest request
    ) {
        OAuthAuthorizationService.AuthorizationUrlResult result =
                oAuthAuthorizationService.startAuthorization(
                        new OAuthAuthorizationCommand(
                                "kakao",
                                request.clientChannel(),
                                request.returnTo()
                        )
                );
        return ApiResponse.ok(
                new KakaoAuthorizationResponse(result.authorizationUrl().toString())
        );
    }

    @PostMapping("/oauth/kakao/exchange")
    public ResponseEntity<ApiResponse<KakaoExchangeResponse>> exchangeKakaoCode(
            @Valid @RequestBody KakaoExchangeRequest request
    ) {
        OAuthLoginResult result = oAuthLoginService.completeLogin(
                new OAuthCallbackCommand(
                        "kakao",
                        request.code(),
                        request.state()
                )
        );
        return ApiResponse.ok(toKakaoExchangeResponse(result));
    }

    private KakaoExchangeResponse toKakaoExchangeResponse(OAuthLoginResult result) {
        if (result instanceof OAuthLoginResult.ExistingAccountAuthenticated authenticated) {
            return new KakaoExchangeResponse(
                    authenticated.tokens().accessToken(),
                    authenticated.tokens().refreshToken()
            );
        }

        if (result instanceof OAuthLoginResult.NewAccountOnboardingRequired ignored) {
            throw new CustomException(ExceptionStatus.OAUTH_ACCOUNT_NOT_LINKED);
        }

        if (result instanceof OAuthLoginResult.ExternalIdentityConflict conflict) {
            if (conflict.reason() == OAuthLoginResult.ConflictReason.ACCOUNT_INACTIVE) {
                throw new CustomException(ExceptionStatus.OAUTH_IDENTITY_CONFLICT);
            }

            if (conflict.reason() == OAuthLoginResult.ConflictReason.ORPHANED_IDENTITY) {
                throw new CustomException(ExceptionStatus.OAUTH_IDENTITY_CONFLICT);
            }
        }

        if (result instanceof OAuthLoginResult.ProviderAuthenticationFailed ignored) {
            throw new CustomException(ExceptionStatus.OAUTH_PROVIDER_AUTH_FAILED);
        }

        if (result instanceof OAuthLoginResult.InvalidOrExpiredState) {
            throw new CustomException(ExceptionStatus.INVALID_OAUTH_STATE);
        }

        throw new IllegalStateException("지원하지 않는 OAuth 로그인 결과입니다.");
    }
}
