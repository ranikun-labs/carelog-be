package carelog.carelog.auth.web;

import carelog.carelog.auth.app.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import org.springframework.web.filter.*;

import java.io.*;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private final String internalSecret;

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

            // SecurityContext에 인증 정보 설정
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Gateway auth set for user: {}", userId);
        }

        filterChain.doFilter(request, response);
    }
}
