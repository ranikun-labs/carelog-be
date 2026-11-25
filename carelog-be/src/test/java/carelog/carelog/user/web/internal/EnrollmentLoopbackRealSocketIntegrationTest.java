package carelog.carelog.user.web.internal;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 Tomcat + 실제 소켓으로 피어 경계를 검증한다. MockMvc는 remoteAddr를 직접 지정하므로, 이 테스트는
 * 스푸핑 경로(X-Forwarded-For)가 소켓 피어를 바꾸지 못함을 실제 TCP 연결로 증명한다.
 *
 * <p>loopback이 아닌 피어를 만들려면 호스트의 non-loopback IPv4 주소로 연결해야 한다. 그런 인터페이스가 없는
 * 환경에서는 가정 검증을 건너뛴다(skip). 루프백 대조군(127.0.0.1)은 항상 실행한다.
 */
@SpringBootTest(classes = CarelogApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "carelog.identity.enrollment.enabled=true",
        "carelog.identity.enrollment.service-secret=test-write-credential-01234567890123456789",
        "carelog.internal.identity-claims.enabled=false",
        "gateway.internal-secret=test-gateway-credential-012345678901234567",
        "carelog.internal.loopback-only.enabled=true"
})
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
class EnrollmentLoopbackRealSocketIntegrationTest {

    private static final String WRITE = "test-write-credential-01234567890123456789";

    @LocalServerPort int port;
    @Value("${server.servlet.context-path:}") String contextPath;

    @Test
    void loopbackSocketPeerIsAcceptedAsControl() throws IOException {
        String status = firstLine(send("127.0.0.1", true));
        assertThat(status).startsWith("HTTP/1.1 201");
    }

    @Test
    void nonLoopbackSocketPeerIsForbiddenEvenWithForwardedForLoopbackAndValidCredential() throws IOException {
        Optional<InetAddress> nonLoopback = nonLoopbackIpv4();
        assumeTrue(nonLoopback.isPresent(), "non-loopback IPv4 인터페이스가 없어 실소켓 가정 검증을 건너뜀");

        String status = firstLine(send(nonLoopback.get().getHostAddress(), true));
        assertThat(status).startsWith("HTTP/1.1 403");
    }

    private String send(String host, boolean spoofForwardedFor) throws IOException {
        UUID id = UUID.randomUUID();
        String userId = "u" + id;
        String body = "{\"userId\":\"" + userId + "\",\"email\":\"" + userId + "@example.test\",\"name\":\"Test\"}";
        StringBuilder request = new StringBuilder()
                .append("PUT ").append(contextPath).append("/internal/v1/identity/carelog/enrollments/").append(id)
                .append(" HTTP/1.1\r\n")
                .append("Host: localhost\r\n")
                .append("Authorization: Bearer ").append(WRITE).append("\r\n");
        if (spoofForwardedFor) {
            request.append("X-Forwarded-For: 127.0.0.1\r\n");
        }
        request.append("Content-Type: application/json\r\n")
                .append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n")
                .append("Connection: close\r\n\r\n")
                .append(body);
        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            return new String(readAll(socket.getInputStream()), StandardCharsets.UTF_8);
        }
    }

    private static String firstLine(String response) {
        int end = response.indexOf("\r\n");
        return end < 0 ? response : response.substring(0, end);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static Optional<InetAddress> nonLoopbackIpv4() throws IOException {
        for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!nic.isUp() || nic.isLoopback()) continue;
            for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return Optional.of(address);
                }
            }
        }
        return Optional.empty();
    }
}
