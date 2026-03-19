package carelog.carelog.auth.web;

import carelog.carelog.auth.app.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 1. 헤더에서 토큰 추출
            String jwt = extractTokenFromHeader(request);

            // 2. 토큰 존재/유효성 검증
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // 3. 토큰에서 사용자 id  추출
                String userId = jwtTokenProvider.getUserIdFromToken(jwt);
                
                //4. UserDetailService로 사용자 정보 조회
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);
                
                // 5. Auth 객체 생성
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                // 6. 요청 정보 auth에 추가
                auth.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // 7. SecurityContext 인증 정보 설정
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("Set Auth for user : {}", userId);
            }
        }  catch (JwtException e) {
            log.error("JWT token error: {}", e.getMessage());
        } catch (UsernameNotFoundException e) {
            log.error("User not found: {}", e.getMessage());
        } catch (Exception e) {
            log.error("인증 처리 중 예상치 못한 오류가 발생했습니다.", e);
        }


        // 8. 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    /**
     * HTTP 요청 헤더에서 JWT 토큰 추출
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;

    }
}

