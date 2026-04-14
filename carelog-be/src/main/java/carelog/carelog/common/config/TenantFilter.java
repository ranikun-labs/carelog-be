package carelog.carelog.common.config;

import carelog.carelog.auth.app.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
//@Component
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null &&
        authentication.getPrincipal() instanceof UserPrincipal userDetails
        ) {
            //  ThreadLocal에 저장
            TenantContext.set(userDetails.getOrganizationId());
            log.debug("Tenant filter activated - organizationId: {}",
                    userDetails.getOrganizationId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // [중요] ThreadLocal 누수 방지
            TenantContext.clear();
        }
    }
}
