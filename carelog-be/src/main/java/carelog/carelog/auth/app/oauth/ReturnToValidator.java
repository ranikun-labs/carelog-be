package carelog.carelog.auth.app.oauth;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** returnTo를 내부 상대 경로 또는 설정된 정확한 origin으로 제한한다. */
@Component
public class ReturnToValidator {

    private final List<String> allowedOrigins;

    @Autowired
    public ReturnToValidator(Environment environment) {
        this(Binder.get(environment)
                .bind("oauth.allowed-return-origins", Bindable.listOf(String.class))
                .orElse(List.of()));
    }

    ReturnToValidator(List<String> allowedOrigins) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
    }

    public String validate(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            throw invalidReturnTo();
        }
        if (isSafeRelativePath(returnTo)) {
            return returnTo;
        }

        try {
            URI uri = new URI(returnTo);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
                throw invalidReturnTo();
            }
            String origin = uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
            if (!allowedOrigins.contains(origin)) {
                throw invalidReturnTo();
            }
            return returnTo;
        } catch (URISyntaxException e) {
            throw invalidReturnTo();
        }
    }

    private boolean isSafeRelativePath(String returnTo) {
        if (containsControlCharacter(returnTo)
                || returnTo.contains("\\")
                || containsEncodedSeparator(returnTo)) {
            return false;
        }

        try {
            URI uri = new URI(returnTo);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || !returnTo.startsWith("/")) {
                return false;
            }

            String decoded = URLDecoder.decode(returnTo, StandardCharsets.UTF_8);
            return !containsControlCharacter(decoded)
                    && !decoded.contains("\\")
                    && !decoded.startsWith("//");
        } catch (IllegalArgumentException | URISyntaxException e) {
            return false;
        }
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private boolean containsEncodedSeparator(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("%2f") || normalized.contains("%5c");
    }

    private CustomException invalidReturnTo() {
        return new CustomException(ExceptionStatus.INVALID_OAUTH_RETURN_TO);
    }
}
