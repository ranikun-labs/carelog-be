package carelog.carelog.auth.app.oauth;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/** returnTo를 검증된 ASCII 로컬 path와 query로만 제한한다. */
@Component
public class ReturnToValidator {

    private static final int MAX_RETURN_TO_LENGTH = 2048;

    public ReturnToValidator() {
    }

    ReturnToValidator(List<String> ignoredAllowedOrigins) {
        this();
    }

    public String validate(String returnTo) {
        if (returnTo == null
                || returnTo.isBlank()
                || returnTo.length() > MAX_RETURN_TO_LENGTH
                || !isAscii(returnTo)
                || containsWhitespaceOrControlCharacter(returnTo)
                || returnTo.contains("\\")
                || returnTo.contains("%")
                || returnTo.contains("#")
                || !returnTo.startsWith("/")
                || returnTo.startsWith("//")) {
            throw invalidReturnTo();
        }

        try {
            URI uri = new URI(returnTo);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawFragment() != null) {
                throw invalidReturnTo();
            }

            String path = pathPart(returnTo);
            if (containsDotSegment(path)) {
                throw invalidReturnTo();
            }
            return returnTo;
        } catch (URISyntaxException e) {
            throw invalidReturnTo();
        }
    }

    private String pathPart(String returnTo) {
        int queryStart = returnTo.indexOf('?');
        return queryStart == -1 ? returnTo : returnTo.substring(0, queryStart);
    }

    private boolean containsDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private boolean isAscii(String value) {
        return value.chars().allMatch(character -> character <= 0x7F);
    }

    private boolean containsWhitespaceOrControlCharacter(String value) {
        return value.codePoints().anyMatch(character ->
                Character.isWhitespace(character) || Character.isISOControl(character));
    }

    private CustomException invalidReturnTo() {
        return new CustomException(ExceptionStatus.INVALID_OAUTH_RETURN_TO);
    }
}
