package carelog.carelog.auth.web;

import carelog.carelog.auth.app.GatewayUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    @Value("${gateway.internal-secret}")
    private String internalSecret;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // X-Gateway-Secret 검증 => 불일치 시 403
        String secret = request.getHeader("X-Gateway-Secret");
        if (secret == null || !secret.equals(internalSecret)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }

        // 헤더에서 유저 정보 추출
        String userId = request.getHeader("X-User-Id");
        String organizationId = request.getHeader("X-Organization-Id");
        String role = request.getHeader("X-Role");
        String publicId = request.getHeader("X-Public-Id");

        if (userId != null && !userId.isBlank()) {
            GatewayUserDetails userDetails = new GatewayUserDetails(
                    userId,
                    UUID.fromString(organizationId),
                    role,
                    UUID.fromString(publicId)
            );



        }


    }
}
