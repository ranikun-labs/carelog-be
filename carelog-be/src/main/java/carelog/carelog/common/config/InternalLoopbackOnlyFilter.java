package carelog.carelog.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 내부 엔드포인트를 TCP 피어가 loopback인 요청으로만 제한한다.
 *
 * <p>판정 기준은 {@link HttpServletRequest#getRemoteAddr()}(소켓 피어)뿐이며 {@code X-Forwarded-For}
 * 같은 헤더는 보지 않는다. 같은 task netns의 8443 TLS sidecar(127.0.0.1로 연결)만 통과한다.
 * 신뢰 경계와 전제는 {@code docs/operations/internal-loopback-boundary.md}를 따른다.
 *
 * <p>{@code enabled=false}이면 아무 것도 하지 않는 pass-through다. 필터는 항상 체인에 등록하고 활성 여부만
 * 생성자 인자로 받아, 비활성 시 체인 구성이 기존과 동일하게 유지된다.
 *
 * <p>거부는 기존 internal 체인과 같이 본문 없는 403이며, 운영 가시성을 위해 WARN 로그를 남긴다.
 */
public final class InternalLoopbackOnlyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalLoopbackOnlyFilter.class);

    /** Tomcat이 소켓 피어를 문자열로 보고하는 loopback 표기. IPv4-mapped 형식은 의도적으로 제외한다. */
    private static final Set<String> LOOPBACK_PEERS = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final boolean enabled;

    public InternalLoopbackOnlyFilter(boolean enabled) {
        this.enabled = enabled;
    }

    static boolean isLoopbackPeer(String remoteAddr) {
        return remoteAddr != null && LOOPBACK_PEERS.contains(remoteAddr);
    }

    /**
     * 기동 시 호출. 루프백 판정을 헤더 값으로 바꿀 수 있는 설정이 하나라도 유효하면 실패시킨다.
     *
     * <ul>
     *   <li>{@code server.forward-headers-strategy}는 명시적으로 {@code none}이어야 한다. 미설정이면
     *       Spring Boot가 클라우드 플랫폼을 감지해 헤더 신뢰를 켤 수 있으므로, 감지된 플랫폼이
     *       헤더를 신뢰하는 경우도 거부한다.</li>
     *   <li>{@code server.tomcat.remoteip.*} 중 값이 있는 항목이 하나라도 있으면 거부한다
     *       (RemoteIpValve 활성 조건을 보수적으로 포괄한다).</li>
     * </ul>
     */
    public static void requireNoForwardedAddressResolution(Environment environment) {
        String strategy = environment.getProperty("server.forward-headers-strategy");
        if (strategy == null || strategy.isBlank()) {
            CloudPlatform platform = CloudPlatform.getActive(environment);
            if (platform != null && platform.isUsingForwardHeaders()) {
                throw loopbackConflict("감지된 클라우드 플랫폼이 forward 헤더를 신뢰하도록 설정된다. "
                        + "server.forward-headers-strategy=none을 명시해야 한다");
            }
        } else if (!"none".equalsIgnoreCase(strategy.trim())) {
            throw loopbackConflict("server.forward-headers-strategy=" + strategy + " (none만 허용)");
        }
        Map<String, String> remoteIp = Binder.get(environment)
                .bind("server.tomcat.remoteip", Bindable.mapOf(String.class, String.class))
                .orElse(Map.of());
        remoteIp.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                throw loopbackConflict("server.tomcat.remoteip." + key + " 설정 존재");
            }
        });
    }

    private static IllegalStateException loopbackConflict(String detail) {
        return new IllegalStateException(
                "internal loopback-only 활성화 시 원격 주소를 헤더로 복원하는 설정은 허용되지 않는다 (remoteAddr 스푸핑 방지): "
                        + detail);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || isLoopbackPeer(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("internal loopback-only 거부: path={}, peer={}", request.getRequestURI(), request.getRemoteAddr());
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
