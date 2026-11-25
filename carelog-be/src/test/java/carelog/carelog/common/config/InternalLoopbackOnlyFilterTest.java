package carelog.carelog.common.config;

import carelog.carelog.auth.web.JwtAccessDeniedHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalLoopbackOnlyFilterTest {

    private static final String PATH = "/internal/v1/identity/carelog/enrollments/a";

    private final JwtAccessDeniedHandler deniedHandler = new JwtAccessDeniedHandler(
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void enabled_passesIpv4Loopback() throws Exception {
        assertPassesThrough(true, "127.0.0.1", null);
    }

    @Test
    void enabled_passesIpv6Loopback() throws Exception {
        assertPassesThrough(true, "0:0:0:0:0:0:0:1", null);
        assertPassesThrough(true, "::1", null);
    }

    @Test
    void enabled_deniesVpcPeerWith403JsonShape() throws Exception {
        MockHttpServletRequest request = request("10.0.0.5", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(true).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"error\":\"Forbidden\"")
                .contains("\"path\":\"" + PATH + "\"");
    }

    @Test
    void enabled_ignoresForwardedForSpoof() throws Exception {
        // 소켓 피어가 VPC면 XFF/X-Real-IP에 loopback을 넣어도 거부된다.
        MockHttpServletRequest request = request("10.0.0.5", null);
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        request.addHeader("X-Real-IP", "::1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(true).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void disabled_isPassThroughForAnyPeer() throws Exception {
        assertPassesThrough(false, "10.0.0.5", null);
    }

    @Test
    void nullRemoteAddr_isDenied() throws Exception {
        MockHttpServletRequest request = request(null, null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter(true).doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void forwardedAddressResolution_failsStartupWhenEnabled() {
        assertThatThrownBy(() -> InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution("framework", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution("native", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution("none", "X-Forwarded-For"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void forwardedAddressResolution_allowsDefaults() {
        InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution("none", "");
        InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution("", null);
    }

    private void assertPassesThrough(boolean enabled, String remoteAddr, String xff) throws Exception {
        MockHttpServletRequest request = request(remoteAddr, xff);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter(enabled).doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private InternalLoopbackOnlyFilter filter(boolean enabled) {
        return new InternalLoopbackOnlyFilter(enabled, deniedHandler);
    }

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", PATH);
        request.setRemoteAddr(remoteAddr);
        if (xff != null) request.addHeader("X-Forwarded-For", xff);
        return request;
    }
}
