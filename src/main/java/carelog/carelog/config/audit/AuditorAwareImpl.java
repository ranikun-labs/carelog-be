package carelog.carelog.config.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(SYSTEM);
            }

            Object principal = authentication.getPrincipal();

            if (principal instanceof UserDetails userDetails) {
                String username = userDetails.getUsername();
                return Optional.ofNullable(username)
                        .filter(s -> !s.isBlank())
                        .or(() -> Optional.of(SYSTEM));
            }

            if (principal instanceof String s) {
                if (s.isBlank() || "anonymousUser".equalsIgnoreCase(s)) {
                    return Optional.of(SYSTEM);
                }
                return Optional.of(s);
            }
        } catch (Exception ignored) {
            // Fall through to default SYSTEM user
        }

        return Optional.of(SYSTEM);
    }
}