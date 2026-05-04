package carelog.carelog.common.config

import carelog.carelog.auth.app.CustomUserDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class TenantFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(TenantFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal

        if (principal is CustomUserDetails) {
            TenantContext.set(principal.organizationId)
            log.debug("Tenant filter activated - organizationId: {}", principal.organizationId)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }
}
