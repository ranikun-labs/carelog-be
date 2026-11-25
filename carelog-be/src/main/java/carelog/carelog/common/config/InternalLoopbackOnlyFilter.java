package carelog.carelog.common.config;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 내부 엔드포인트를 TCP 피어가 loopback인 요청으로만 제한한다.
 *
 * <p>판정 기준은 {@link HttpServletRequest#getRemoteAddr()}(소켓 피어)뿐이며 {@code X-Forwarded-For}
 * 같은 헤더는 보지 않는다. 따라서 VPC의 8080 직접 접근은 거부되고, 같은 task의 8443 TLS sidecar
 * (127.0.0.1로 연결)만 통과한다.
 *
 * <p>{@code enabled=false}이면 아무 것도 하지 않는 pass-through다. 이 필터는 항상 체인에 등록하되
 * 활성 여부만 생성자 인자로 받아, 비활성 시 체인 구성이 기존과 동일하게 유지된다.
 *
 * <p>주의: 이 보장은 {@code server.forward-headers-strategy}와 {@code server.tomcat.remoteip.*}가
 * 꺼져 있을 때만 성립한다. 두 설정 중 하나라도 켜지면 {@code remoteAddr}가 헤더 값으로 바뀔 수 있으므로
 * {@link #requireNoForwardedAddressResolution(String, String)}가 기동을 실패시킨다.
 */
public final class InternalLoopbackOnlyFilter extends OncePerRequestFilter {

    /** Tomcat이 소켓 피어를 문자열로 보고하는 loopback 표기. IPv4-mapped 형식은 의도적으로 제외한다. */
    private static final Set<String> LOOPBACK_PEERS = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final boolean enabled;
    private final JwtAccessDeniedHandler deniedHandler;

    public InternalLoopbackOnlyFilter(boolean enabled, JwtAccessDeniedHandler deniedHandler) {
        this.enabled = enabled;
        this.deniedHandler = deniedHandler;
    }

    static boolean isLoopbackPeer(String remoteAddr) {
        return remoteAddr != null && LOOPBACK_PEERS.contains(remoteAddr);
    }

    /**
     * 기동 시 호출. 헤더로 원격 주소를 복원하는 설정이 켜져 있으면 loopback 판정이 스푸핑 가능해지므로
     * 실패시킨다. 기본값({@code none}, 빈 값)에서만 통과한다.
     */
    public static void requireNoForwardedAddressResolution(String forwardHeadersStrategy, String remoteIpHeader) {
        boolean strategyOff = forwardHeadersStrategy == null
                || forwardHeadersStrategy.isBlank()
                || "none".equalsIgnoreCase(forwardHeadersStrategy.trim());
        boolean remoteIpOff = remoteIpHeader == null || remoteIpHeader.isBlank();
        if (!strategyOff || !remoteIpOff) {
            throw new IllegalStateException(
                    "carelog.internal.loopback-only 활성화 시 server.forward-headers-strategy와 "
                            + "server.tomcat.remoteip.remote-ip-header는 비활성이어야 한다 (remoteAddr 스푸핑 방지)");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || isLoopbackPeer(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.clearContext();
        deniedHandler.handle(request, response,
                new AccessDeniedException("internal endpoint is restricted to loopback peers"));
    }
}
