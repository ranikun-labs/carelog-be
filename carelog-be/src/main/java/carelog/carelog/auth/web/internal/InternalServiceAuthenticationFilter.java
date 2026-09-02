package carelog.carelog.auth.web.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 내부 경로에서 고정된 {@code platform-identity} 서비스 Principal만 설정한다.
 *
 * <p>{@link InternalIdentitySecurityConfiguration}에 의해서만 조건부로 등록되며, 독립적인
 * 범용 servlet filter로 등록되지 않는다({@code FilterRegistrationBean.setEnabled(false)}).
 * 사용자/Gateway 인증 헤더가 함께 들어오면 대체·증강 수단으로 사용하지 않고 즉시 거부한다.
 */
public final class InternalServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> USER_OR_GATEWAY_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            "X-Gateway-Secret",
            "X-User-Id",
            "X-Organization-Id",
            "X-Role",
            "X-Public-Id");

    private final InternalServiceCredentialVerifier credentialVerifier;

    public InternalServiceAuthenticationFilter(InternalServiceCredentialVerifier credentialVerifier) {
        this.credentialVerifier = credentialVerifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !("/internal".equals(path) || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isValidServiceRequest(request)) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                credentialVerifier.authenticatedPrincipal());
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isValidServiceRequest(HttpServletRequest request) {
        if (USER_OR_GATEWAY_HEADERS.stream().anyMatch(header -> request.getHeader(header) != null)) {
            return false;
        }
        return credentialVerifier.matches(
                request.getHeader("X-Service-Id"),
                request.getHeader("X-Service-Secret"));
    }
}
