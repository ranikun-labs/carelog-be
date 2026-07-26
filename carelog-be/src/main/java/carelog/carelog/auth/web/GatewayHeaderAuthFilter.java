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
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

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
            if (organizationId == null || publicId == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing required auth headers");
                return;
            }
            UUID parsedAccountId;
            UUID parsedOrgId;
            UUID parsedPublicId;
            try {
                // X-User-Id는 Identity Foundation B0부터 accountId(UUID)다(과거 loginId 문자열 대체).
                parsedAccountId = UUID.fromString(userId);
                parsedOrgId = UUID.fromString(organizationId);
                parsedPublicId = UUID.fromString(publicId);
            } catch (IllegalArgumentException e) {
                log.warn("Malformed UUID in gateway headers: userId={}, organizationId={}, publicId={}",
                        userId, organizationId, publicId);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Malformed auth headers");
                return;
            }
            GatewayUserDetails userDetails = new GatewayUserDetails(
                    parsedAccountId,
                    parsedOrgId,
                    role,
                    parsedPublicId
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
