package carelog.carelog.user.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Identity claims private API 전용 service token 검증 필터.
 *
 * <p>Gateway secret, 사용자 Bearer JWT, X-User-* header는 이 경계의 인증 수단이 아니다.
 * 예상 token은 runtime configuration에서만 주입되며 로그나 response에 기록하지 않는다.
 */
public class IdentityClaimsServiceTokenFilter extends OncePerRequestFilter {

    public static final String SERVICE_TOKEN_HEADER = "X-Platform-Service-Token";

    private final byte[] expectedToken;

    public IdentityClaimsServiceTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String presentedToken = request.getHeader(SERVICE_TOKEN_HEADER);
        if (!matches(presentedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(String presentedToken) {
        if (presentedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken,
                presentedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
