package carelog.carelog.common.config.audit

import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

import java.util.Optional;

class AuditorAwareImpl : AuditorAware<String> {

    companion object {
        private const val SYSTEM = "SYSTEM"
    }
    override fun getCurrentAuditor(): Optional<String> {
        return try {
            val authentication = SecurityContextHolder.getContext().authentication

            if (authentication == null || !authentication.isAuthenticated) {
                return Optional.of(SYSTEM)
            }

            when (val pricipal = authentication.principal) {
                is UserDetails -> Optional.ofNullable(pricipal.username)
                    .filter { it.isNotBlank() }
                    .or { Optional.of(SYSTEM) }

                is String -> when {
                    pricipal.isBlank() ||
                            pricipal.equals(
                                "anonymousUser", ignoreCase = true
                            ) ->
                        Optional.of(SYSTEM)
                    else -> Optional.of(pricipal)
                }
                else -> Optional.of(SYSTEM)
            }
        } catch (e: Exception) {
            Optional.of(SYSTEM)
        }
    }
}