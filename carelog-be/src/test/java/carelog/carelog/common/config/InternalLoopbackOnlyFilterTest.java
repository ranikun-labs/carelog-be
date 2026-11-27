package carelog.carelog.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalLoopbackOnlyFilterTest {

    private static final String PATH = "/internal/v1/identity/carelog/enrollments/a";

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
    void enabled_deniesVpcPeerWithEmptyBodyForbidden() throws Exception {
        MockHttpServletRequest request = request("10.0.0.5", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalLoopbackOnlyFilter(true).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void enabled_ignoresForwardedHeadersSpoof() throws Exception {
        // 소켓 피어가 VPC면 XFF/X-Real-IP에 loopback을 넣어도 거부된다.
        MockHttpServletRequest request = request("10.0.0.5", "127.0.0.1");
        request.addHeader("X-Real-IP", "::1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalLoopbackOnlyFilter(true).doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void disabled_isPassThroughForAnyPeer() throws Exception {
        assertPassesThrough(false, "10.0.0.5", null);
    }

    @Test
    void nullRemoteAddr_isDenied() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new InternalLoopbackOnlyFilter(true).doFilter(request(null, null), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void guard_allowsExplicitNone() {
        assertThatCode(() -> InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution(
                new MockEnvironment().withProperty("server.forward-headers-strategy", "none")))
                .doesNotThrowAnyException();
    }

    @Test
    void guard_allowsWhenNothingIsSetAndNoCloudPlatform() {
        assertThatCode(() -> InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution(new MockEnvironment()))
                .doesNotThrowAnyException();
    }

    @Test
    void guard_rejectsFrameworkAndNativeStrategies() {
        assertRejected(new MockEnvironment().withProperty("server.forward-headers-strategy", "framework"));
        assertRejected(new MockEnvironment().withProperty("server.forward-headers-strategy", "native"));
    }

    @Test
    void guard_rejectsRemoteIpValveProperties() {
        assertRejected(new MockEnvironment().withProperty("server.tomcat.remoteip.remote-ip-header", "X-Forwarded-For"));
        assertRejected(new MockEnvironment().withProperty("server.tomcat.remoteip.protocol-header", "X-Forwarded-Proto"));
        assertRejected(new MockEnvironment().withProperty("server.tomcat.remoteip.internal-proxies", "10\\.0\\.0\\.\\d+"));
    }

    @Test
    void guard_rejectsCloudPlatformThatTrustsForwardHeadersWhenStrategyUnset() {
        // strategy 미설정 시 Spring Boot가 클라우드 플랫폼 기본값으로 헤더 신뢰를 켤 수 있다(CLOUD_FOUNDRY는 신뢰).
        assertRejected(new MockEnvironment().withProperty("spring.main.cloud-platform", "CLOUD_FOUNDRY"));
    }

    private static void assertRejected(MockEnvironment env) {
        assertThatThrownBy(() -> InternalLoopbackOnlyFilter.requireNoForwardedAddressResolution(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("스푸핑 방지");
    }

    private void assertPassesThrough(boolean enabled, String remoteAddr, String xff) throws Exception {
        MockHttpServletRequest request = request(remoteAddr, xff);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new InternalLoopbackOnlyFilter(enabled).doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", PATH);
        request.setRemoteAddr(remoteAddr);
        if (xff != null) request.addHeader("X-Forwarded-For", xff);
        return request;
    }
}
