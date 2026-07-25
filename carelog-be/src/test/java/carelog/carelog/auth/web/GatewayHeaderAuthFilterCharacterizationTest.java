package carelog.carelog.auth.web;

import carelog.carelog.auth.app.GatewayUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * GatewayHeaderAuthFilter의 현재 동작을 고정하는 Characterization Test.
 * docs/context/identity/phase-3a-internal-boundary-plan.md §3.2/§11(3A-2)에서
 * 3A-3 착수 전 선행 조건으로 언급된 커버리지 공백을 메운다. 보안 개선이 아니라
 * 현재 분기를 그대로 고정하는 것이 목적이다.
 */
@ExtendWith(MockitoExtension.class)
class GatewayHeaderAuthFilterCharacterizationTest {

    private static final String INTERNAL_SECRET = "test-internal-secret";

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private GatewayHeaderAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderAuthFilter(INTERNAL_SECRET);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @DisplayName("OPTIONS 요청은 shouldNotFilter가 true를 반환해 필터 로직을 건너뛴다")
    @Test
    void shouldNotFilter_optionsRequest_returnsTrue() {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @DisplayName("OPTIONS가 아닌 요청은 shouldNotFilter가 false를 반환한다")
    @Test
    void shouldNotFilter_nonOptionsRequest_returnsFalse() {
        when(request.getMethod()).thenReturn("GET");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @DisplayName("X-Gateway-Secret 헤더가 없으면 403을 반환하고 filterChain을 진행하지 않는다")
    @Test
    void doFilterInternal_missingSecret_returns403WithoutContinuingChain() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        verifyNoInteractions(filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @DisplayName("X-Gateway-Secret 헤더가 내부 시크릿과 다르면 403을 반환하고 filterChain을 진행하지 않는다")
    @Test
    void doFilterInternal_mismatchedSecret_returns403WithoutContinuingChain() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn("wrong-secret");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
        verifyNoInteractions(filterChain);
    }

    @DisplayName("시크릿은 일치하지만 X-User-Id가 없으면 인증 설정 없이 filterChain을 그대로 진행한다")
    @Test
    void doFilterInternal_validSecretNoUserIdHeader_continuesChainWithoutAuthentication() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @DisplayName("시크릿은 일치하지만 X-User-Id가 공백이면 인증 설정 없이 filterChain을 그대로 진행한다")
    @Test
    void doFilterInternal_validSecretBlankUserIdHeader_continuesChainWithoutAuthentication() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @DisplayName("X-User-Id는 있지만 organizationId가 없으면 403을 반환하고 filterChain을 진행하지 않는다")
    @Test
    void doFilterInternal_userIdWithoutOrganizationId_returns403() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("manager@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn(null);
        when(request.getHeader("X-Role")).thenReturn("MANAGER");
        when(request.getHeader("X-Public-Id")).thenReturn(UUID.randomUUID().toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Missing required auth headers");
        verifyNoInteractions(filterChain);
    }

    @DisplayName("X-User-Id는 있지만 publicId가 없으면 403을 반환하고 filterChain을 진행하지 않는다")
    @Test
    void doFilterInternal_userIdWithoutPublicId_returns403() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("manager@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn(UUID.randomUUID().toString());
        when(request.getHeader("X-Role")).thenReturn("MANAGER");
        when(request.getHeader("X-Public-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Missing required auth headers");
        verifyNoInteractions(filterChain);
    }

    @DisplayName("organizationId가 UUID 형식이 아니면 403을 반환하고 filterChain을 진행하지 않는다")
    @Test
    void doFilterInternal_malformedOrganizationId_returns403() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("manager@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn("not-a-uuid");
        when(request.getHeader("X-Role")).thenReturn("MANAGER");
        when(request.getHeader("X-Public-Id")).thenReturn(UUID.randomUUID().toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Malformed auth headers");
        verifyNoInteractions(filterChain);
    }

    @DisplayName("publicId가 UUID 형식이 아니면 403을 반환하고 filterChain을 진행하지 않는다")
    @Test
    void doFilterInternal_malformedPublicId_returns403() throws Exception {
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("manager@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn(UUID.randomUUID().toString());
        when(request.getHeader("X-Role")).thenReturn("MANAGER");
        when(request.getHeader("X-Public-Id")).thenReturn("not-a-uuid");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Malformed auth headers");
        verifyNoInteractions(filterChain);
    }

    @DisplayName("모든 헤더가 유효하면 GatewayUserDetails로 SecurityContext를 채우고 filterChain을 진행한다")
    @Test
    void doFilterInternal_validHeaders_setsSecurityContextAndContinuesChain() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("manager@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn(organizationId.toString());
        when(request.getHeader("X-Role")).thenReturn("MANAGER");
        when(request.getHeader("X-Public-Id")).thenReturn(publicId.toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(GatewayUserDetails.class);
        GatewayUserDetails principal = (GatewayUserDetails) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo("manager@example.com");
        assertThat(principal.getOrganizationId()).isEqualTo(organizationId);
        assertThat(principal.getRole()).isEqualTo("MANAGER");
        assertThat(principal.getPublicId()).isEqualTo(publicId);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_MANAGER");
    }

    @DisplayName("[Known Limitation] X-Role 헤더가 없으면 role=null 상태로 인증을 설정하고 권한 문자열은 ROLE_null이 된다")
    @Test
    void doFilterInternal_missingRoleHeader_setsNullRoleAndRoleNullAuthority() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("manager@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn(organizationId.toString());
        when(request.getHeader("X-Role")).thenReturn(null);
        when(request.getHeader("X-Public-Id")).thenReturn(publicId.toString());

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        GatewayUserDetails principal = (GatewayUserDetails) authentication.getPrincipal();
        assertThat(principal.getRole()).isNull();
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_null");
    }

    @DisplayName("[Known Limitation] 시크릿만 일치하면 호출자가 직접 보낸 신원 헤더도 그대로 신뢰한다 - 신뢰 경계는 secret 값 하나뿐이다")
    @Test
    void doFilterInternal_trustsAnyCallerSuppliedIdentityHeadersOnceSecretMatches() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(request.getHeader("X-Gateway-Secret")).thenReturn(INTERNAL_SECRET);
        when(request.getHeader("X-User-Id")).thenReturn("spoofed-admin@example.com");
        when(request.getHeader("X-Organization-Id")).thenReturn(organizationId.toString());
        when(request.getHeader("X-Role")).thenReturn("MANAGER");
        when(request.getHeader("X-Public-Id")).thenReturn(publicId.toString());

        filter.doFilterInternal(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        GatewayUserDetails principal = (GatewayUserDetails) authentication.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo("spoofed-admin@example.com");
        // Gateway를 거치지 않고 secret만 알면, 동일 이름 헤더로 임의 신원을 주장할 수 있는
        // 현재 신뢰 모델의 한계(개별 헤더에 대한 서명/추가 검증 없음).
    }
}
